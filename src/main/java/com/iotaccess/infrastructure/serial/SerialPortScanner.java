package com.iotaccess.infrastructure.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.iotaccess.domain.model.SerialPortInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Escáner de puertos seriales usando jSerialComm.
 */
@Component
@Slf4j
public class SerialPortScanner {

    /**
     * Obtiene la lista de puertos seriales disponibles en el sistema.
     * 
     * @return Lista de información de puertos
     */
    public List<SerialPortInfo> getAvailablePorts() {
        SerialPort[] ports = SerialPort.getCommPorts();

        log.info("Escaneando puertos seriales. Encontrados: {}", ports.length);

        return Arrays.stream(ports)
                .map(this::toSerialPortInfo)
                .collect(Collectors.toList());
    }

    /**
     * Busca un puerto por su nombre de sistema.
     * 
     * @param portName Nombre del puerto (ej: COM3)
     * @return SerialPort si existe, null si no
     */
    public SerialPort findPort(String portName) {
        SerialPort[] ports = SerialPort.getCommPorts();

        for (SerialPort port : ports) {
            if (port.getSystemPortName().equalsIgnoreCase(portName)) {
                return port;
            }
        }

        log.warn("Puerto no encontrado: {}", portName);
        return null;
    }

    /**
     * Verifica si un puerto específico está disponible.
     * 
     * @param portName Nombre del puerto
     * @return true si está disponible
     */
    public boolean isPortAvailable(String portName) {
        SerialPort port = findPort(portName);
        return port != null && !port.isOpen();
    }

    /**
     * Prueba de conexión: envía PING y espera PONG del firmware correcto.
     *
     * @param portName Nombre del puerto a probar
     * @return Mapa con resultado de la prueba
     */
    public java.util.Map<String, Object> probePort(String portName) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        SerialPort port = findPort(portName);

        if (port == null) {
            result.put("success", false);
            result.put("message", "Puerto no encontrado: " + portName);
            return result;
        }

        if (port.isOpen()) {
            result.put("success", false);
            result.put("message", "El puerto está en uso");
            return result;
        }

        try {
            port.setBaudRate(115200);
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 3000, 0);

            if (!port.openPort()) {
                result.put("success", false);
                result.put("message", "No se pudo abrir el puerto");
                return result;
            }

            // Esperar a que el Arduino reinicie (algunos Arduinos reset al abrir serial)
            Thread.sleep(2000);

            // Limpiar buffer
            while (port.bytesAvailable() > 0) {
                byte[] discard = new byte[port.bytesAvailable()];
                port.readBytes(discard, discard.length);
            }

            // Enviar PING
            byte[] ping = "P\n".getBytes();
            port.writeBytes(ping, ping.length);

            // Esperar respuesta (hasta 3 segundos)
            StringBuilder response = new StringBuilder();
            long startTime = System.currentTimeMillis();

            while (System.currentTimeMillis() - startTime < 3000) {
                if (port.bytesAvailable() > 0) {
                    byte[] buffer = new byte[port.bytesAvailable()];
                    int read = port.readBytes(buffer, buffer.length);
                    response.append(new String(buffer, 0, read));

                    if (response.toString().contains("PONG:")) {
                        break;
                    }
                }
                Thread.sleep(100);
            }

            port.closePort();

            String resp = response.toString().trim();
            log.info("Respuesta de probe en {}: '{}'", portName, resp);

            if (resp.contains("PONG:ACCESS_SYSTEM")) {
                result.put("success", true);
                result.put("message", "✅ Dispositivo verificado: Sistema de Control de Acceso IoT");
                result.put("firmware", resp.substring(resp.indexOf("PONG:") + 5).trim());
            } else if (resp.length() > 0) {
                result.put("success", false);
                result.put("message", "Dispositivo respondió pero no es el firmware correcto");
                result.put("response", resp.substring(0, Math.min(resp.length(), 100)));
            } else {
                result.put("success", false);
                result.put("message", "Sin respuesta — el dispositivo no tiene el firmware correcto");
            }

        } catch (Exception e) {
            log.error("Error en probe de puerto {}: {}", portName, e.getMessage());
            result.put("success", false);
            result.put("message", "Error probando puerto: " + e.getMessage());
            try {
                port.closePort();
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    /**
     * Convierte un SerialPort de jSerialComm a nuestro modelo de dominio.
     */
    private SerialPortInfo toSerialPortInfo(SerialPort port) {
        return SerialPortInfo.builder()
                .systemPortName(port.getSystemPortName())
                .descriptivePortName(port.getDescriptivePortName())
                .isOpen(port.isOpen())
                .build();
    }
}
