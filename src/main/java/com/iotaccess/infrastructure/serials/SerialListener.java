package com.iotaccess.infrastructure.serials;

import com.fazecast.jSerialComm.SerialPort;
import com.iotaccess.utility.exceptions.SerialPortException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listener de puerto serial que procesa datos del Arduino/PN532.
 * Ejecuta en un hilo independiente para no bloquear la aplicación.
 */
@Component
@Slf4j
public class SerialListener {

    // Patrón para parsear UID: "UID:XX-XX-XX-XX" o "UID: XX XX XX XX"
    private static final Pattern UID_PATTERN = Pattern.compile("UID:?\\s*([A-Fa-f0-9\\s\\-]+)");

    @Value("${serial.baud-rate:115200}")
    private int baudRate;

    @Value("${serial.data-bits:8}")
    private int dataBits;

    @Value("${serial.stop-bits:1}")
    private int stopBits;

    @Value("${serial.parity:0}")
    private int parity;

    private final SerialPortScanner portScanner;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SerialPort currentPort;
    private Consumer<String> uidCallback;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SerialListener(SerialPortScanner portScanner) {
        this.portScanner = portScanner;
    }

    /**
     * Inicia la escucha en el puerto especificado.
     * 
     * @param portName      Nombre del puerto (ej: COM3)
     * @param onUidReceived Callback cuando se recibe un UID
     * @throws SerialPortException si no se puede abrir el puerto
     */
    public void start(String portName, Consumer<String> onUidReceived) {
        if (running.get()) {
            log.warn("El listener ya está ejecutándose. Deteniendo primero...");
            stop();
        }

        this.uidCallback = onUidReceived;
        currentPort = portScanner.findPort(portName);

        if (currentPort == null) {
            throw SerialPortException.notFound(portName);
        }

        // Configurar puerto
        currentPort.setBaudRate(baudRate);
        currentPort.setNumDataBits(dataBits);
        currentPort.setNumStopBits(stopBits);
        currentPort.setParity(parity);
        // Usar 100ms de timeout de lectura para evitar bloqueo infinito y prescindir de
        // available()
        currentPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);

        // Intentar abrir
        if (!currentPort.openPort()) {
            throw SerialPortException.cannotOpen(portName);
        }

        running.set(true);
        log.info("Puerto {} abierto exitosamente a {} baudios", portName, baudRate);

        // Iniciar hilo de lectura
        executor.submit(this::readLoop);

        // Activar el Arduino despues del reset DTR (2s de espera)
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000);
                if (running.get()) {
                    sendCommand('A');
                    log.info("Comando 'A' enviado al Arduino para activar sesion");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Detiene la escucha y cierra el puerto.
     */
    public void stop() {
        running.set(false);

        if (currentPort != null && currentPort.isOpen()) {
            currentPort.closePort();
            log.info("Puerto {} cerrado", currentPort.getSystemPortName());
        }

        currentPort = null;
    }

    /**
     * Verifica si el listener está activo.
     */
    public boolean isRunning() {
        return running.get() && currentPort != null && currentPort.isOpen();
    }

    /**
     * Obtiene el nombre del puerto actual.
     */
    public String getCurrentPortName() {
        return currentPort != null ? currentPort.getSystemPortName() : null;
    }

    /**
     * Envía un comando de un carácter al Arduino.
     * Comandos:
     * - 'W': Esperar tarjeta del admin
     * - 'E': Entrar en Modo Enrolamiento (Arduino muestra "MODO REGISTRO")
     * - 'A': Volver a Modo Acceso
     * - 'K': Confirmación de registro exitoso
     * - 'X': Admin rechazado
     * - '1': Acceso concedido
     * - '0': Acceso denegado
     * 
     * @param command Carácter del comando a enviar
     */
    public void sendCommand(char command) {
        if (currentPort == null || !currentPort.isOpen()) {
            log.warn("No se puede enviar comando '{}': puerto no disponible", command);
            return;
        }

        try {
            byte[] data = new byte[] { (byte) command, (byte) '\n' };
            int written = currentPort.writeBytes(data, 2);

            if (written > 0) {
                log.info("Comando '{}' enviado al Arduino exitosamente", command);
            } else {
                log.warn("No se pudo escribir el comando '{}' al puerto", command);
            }
        } catch (Exception e) {
            log.error("Error enviando comando '{}' al Arduino: {}", command, e.getMessage());
        }
    }

    /**
     * Envía un mensaje completo (string) al Arduino.
     * Usado para enviar datos como nombres de usuario.
     * Formato: "COMANDO:DATOS\n"
     * 
     * @param message Mensaje a enviar
     */
    public void sendMessage(String message) {
        if (currentPort == null || !currentPort.isOpen()) {
            log.warn("No se puede enviar mensaje '{}': puerto no disponible", message);
            return;
        }

        try {
            byte[] data = (message + "\n").getBytes();
            int written = currentPort.writeBytes(data, data.length);

            if (written > 0) {
                log.info("Mensaje '{}' enviado al Arduino exitosamente", message);
            } else {
                log.warn("No se pudo escribir el mensaje '{}' al puerto", message);
            }
        } catch (Exception e) {
            log.error("Error enviando mensaje '{}' al Arduino: {}", message, e.getMessage());
        }
    }

    /**
     * Loop principal de lectura del puerto serial.
     */
    private void readLoop() {
        log.info("Iniciando loop de lectura serial...");

        try {
            java.io.InputStream in = currentPort.getInputStream();
            StringBuilder buffer = new StringBuilder();
            byte[] chunk = new byte[1024];

            while (running.get() && currentPort != null && currentPort.isOpen()) {
                try {
                    // Si el puerto fue desconectado físicamente, bytesAvailable devuelve -1
                    if (currentPort.bytesAvailable() == -1) {
                        log.error("Dispositivo serial desconectado físicamente. Deteniendo lectura.");
                        break;
                    }

                    // Si no hay datos disponibles, evitamos bloquear en read() que causa
                    // excepciones de timeout en Windows
                    if (currentPort.bytesAvailable() == 0) {
                        Thread.sleep(50);
                        continue;
                    }

                    int len = in.read(chunk);
                    if (len > 0) {
                        String data = new String(chunk, 0, len);
                        buffer.append(data);

                        int newlineIdx;
                        while ((newlineIdx = buffer.indexOf("\n")) != -1) {
                            String line = buffer.substring(0, newlineIdx).trim();
                            buffer.delete(0, newlineIdx + 1);
                            if (!line.isEmpty()) {
                                log.debug("Línea recibida: {}", line);
                                processLine(line);
                            }
                        }
                    } else if (len == -1) {
                        log.warn("Corriente serial terminada inesperadamente.");
                        break;
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        String errMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                        if (errMsg.contains("shutdown or disconnected")
                                || errMsg.contains("device attached to the system is not functioning")) {
                            log.error(
                                    "Dispositivo serial desconectado físicamente o falló. Deteniendo lectura para liberar puerto: {}",
                                    e.getMessage());
                            break;
                        } else if (errMsg.contains("the read operation timed out")) {
                            // Ignorar, en caso de que ocurra durante read bloqueante
                        } else {
                            log.error("Error leyendo datos seriales: {}", e.getMessage());
                        }

                        Thread.sleep(50);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fatal en loop de lectura serial: {}", e.getMessage(), e);
        } finally {
            if (running.get()) {
                log.info("Cerrando puerto tras finalizar loop anormalmente.");
                stop();
            }
        }

        log.info("Loop de lectura finalizado");
    }

    /**
     * Procesa una línea recibida y extrae el UID si está presente.
     */
    private void processLine(String line) {
        // Log ALL received lines for debugging
        log.info(">>> Serial recibido: [{}]", line);

        // DEBUG: Mostrar bytes exactos para diagnosticar problemas de formato
        StringBuilder hexDump = new StringBuilder();
        for (char c : line.toCharArray()) {
            hexDump.append(String.format("%02X ", (int) c));
        }
        log.info(">>> Bytes hex: {}", hexDump.toString().trim());
        log.info(">>> Longitud: {}, Patrón: {}", line.length(), UID_PATTERN.pattern());

        Matcher matcher = UID_PATTERN.matcher(line);

        if (matcher.find()) {
            String rawGroup = matcher.group(1);
            String uid = rawGroup.trim().toUpperCase();
            log.info("==> UID raw group(1): [{}], length={}", rawGroup, rawGroup.length());
            log.info("==> UID procesado: [{}], length={}", uid, uid.length());

            // DEBUG: hex dump del UID extraído
            StringBuilder uidHex = new StringBuilder();
            for (char c : uid.toCharArray()) {
                uidHex.append(String.format("%02X ", (int) c));
            }
            log.info("==> UID bytes: {}", uidHex.toString().trim());

            if (uidCallback != null) {
                try {
                    // Llamar DIRECTAMENTE en el hilo del lector serial
                    // para que la respuesta al Arduino sea INMEDIATA.
                    // processIncomingUid() envia la respuesta primero
                    // y luego las ops lentas las hace en background.
                    uidCallback.accept(uid);
                } catch (Exception e) {
                    log.error("Error en callback de UID: {}", e.getMessage(), e);
                }
            }
        } else {
            log.info("!!! Línea NO coincide con patrón UID: [{}]", line);
        }
    }

    /**
     * Cierra recursos al destruir el componente.
     */
    @jakarta.annotation.PreDestroy
    public void cleanup() {
        log.info("Limpiando recursos del SerialListener...");
        stop();
        executor.shutdown();
    }
}
