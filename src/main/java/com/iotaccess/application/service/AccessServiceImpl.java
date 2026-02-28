package com.iotaccess.application.service;

import com.iotaccess.application.dto.AccessRecordDto;
import com.iotaccess.application.dto.SessionStatusDto;
import com.iotaccess.domain.model.AccessRecord;
import com.iotaccess.domain.model.AccessStatus;
import com.iotaccess.domain.model.CaptureSession;
import com.iotaccess.domain.model.SerialPortInfo;
import com.iotaccess.domain.port.AccessLogWriter;
import com.iotaccess.domain.port.AccessRepository;
import com.iotaccess.domain.port.UserRegistryPort;
import com.iotaccess.infrastructure.serial.SerialListener;
import com.iotaccess.infrastructure.serial.SerialPortScanner;
import com.iotaccess.presentation.websocket.AccessWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Implementación del servicio principal de acceso.
 * Coordina la lógica de negocio entre las capas de infraestructura y
 * presentación.
 */
@Service
@Slf4j
public class AccessServiceImpl implements AccessService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SerialPortScanner portScanner;
    private final SerialListener serialListener;
    private final AccessLogWriter logWriter;
    private final AccessRepository accessRepository;
    private final AccessWebSocketHandler webSocketHandler;
    private final UserRegistryPort userRegistryPort;

    // Inyección lazy para evitar dependencia circular
    @Autowired
    @Lazy
    private DeviceManagementService deviceManagementService;

    @Value("${station.id:1}")
    private Integer stationId;

    // UID del administrador desde configuración
    @Value("${admin.uid:EB-EE-C0-1}")
    private String adminUid;

    // Almacenamiento en memoria de la sesión actual
    private CaptureSession currentSession;

    // Cache de registros del día para la UI (thread-safe)
    private final CopyOnWriteArrayList<AccessRecord> todayRecords = new CopyOnWriteArrayList<>();

    public AccessServiceImpl(
            SerialPortScanner portScanner,
            SerialListener serialListener,
            AccessLogWriter logWriter,
            AccessRepository accessRepository,
            AccessWebSocketHandler webSocketHandler,
            UserRegistryPort userRegistryPort) {
        this.portScanner = portScanner;
        this.serialListener = serialListener;
        this.logWriter = logWriter;
        this.accessRepository = accessRepository;
        this.webSocketHandler = webSocketHandler;
        this.userRegistryPort = userRegistryPort;
    }

    @Override
    public List<SerialPortInfo> getAvailablePorts() {
        return portScanner.getAvailablePorts();
    }

    @Override
    public synchronized void startSession(String portName, String sessionName) {
        if (currentSession != null && currentSession.isActive()) {
            throw new IllegalStateException("Ya hay una sesión activa. Deténgala primero.");
        }

        log.info("Iniciando sesión: puerto={}, nombre={}", portName, sessionName);

        // Limpiar registros anteriores
        todayRecords.clear();

        // Inicializar el writer CSV
        String csvPath = logWriter.initialize(sessionName);

        // Iniciar el listener serial
        serialListener.start(portName, this::processIncomingUid);

        // Crear la sesión
        currentSession = CaptureSession.start(portName, sessionName);
        currentSession.setCsvFilePath(csvPath);

        log.info("Sesión iniciada exitosamente. Archivo: {}", csvPath);
    }

    @Override
    public synchronized void reconnectSession(String portName) {
        // Si hay sesión activa, solo detener el serial (no el CSV)
        if (currentSession != null && currentSession.isActive()) {
            serialListener.stop();
        }

        // Verificar si el CSV writer aún tiene un archivo activo
        if (!logWriter.isReady()) {
            throw new IllegalStateException(
                    "No hay sesión CSV previa. Use 'Iniciar sesión' en su lugar.");
        }

        log.info("⟳ Reconectando Arduino en puerto={}, continuando CSV existente", portName);

        // Reiniciar el listener serial con el nuevo puerto
        serialListener.start(portName, this::processIncomingUid);

        // Reactivar la sesión con el nuevo puerto
        if (currentSession != null) {
            currentSession.setActive(true);
            currentSession.setPortName(portName);
        } else {
            // Crear nueva sesión pero reutilizando el CSV existente
            currentSession = CaptureSession.start(portName, "reconnected");
            currentSession.setCsvFilePath(
                    ((com.iotaccess.infrastructure.file.CsvAccessLogWriter) logWriter)
                            .getCurrentFilePath());
        }

        log.info("✓ Arduino reconectado exitosamente. CSV activo: {}",
                currentSession.getCsvFilePath());
    }

    @Override
    public synchronized void renameCurrentSession(String newName) {
        if (currentSession == null || !currentSession.isActive()) {
            throw new IllegalStateException("No hay sesión activa para renombrar");
        }

        log.info("Renombrando sesión '{}' → '{}'", currentSession.getSessionName(), newName);

        // Renombrar archivos CSV (principal + backup + binario)
        String newPath = logWriter.renameFile(newName);

        // Actualizar la sesión en memoria
        currentSession.setSessionName(newName);
        currentSession.setCsvFilePath(newPath);

        log.info("✓ Sesión renombrada exitosamente: {}", newPath);
    }

    @Override
    public synchronized void stopSession() {
        if (currentSession == null || !currentSession.isActive()) {
            log.warn("No hay sesión activa para detener");
            return;
        }

        log.info("Deteniendo sesión: {}", currentSession.getSessionName());

        // Detener listener
        serialListener.stop();

        // Cerrar writer
        logWriter.close();

        // Marcar sesión como inactiva
        currentSession.setActive(false);

        log.info("Sesión detenida. Total registros: {}", currentSession.getRecordCount());
    }

    @Override
    public SessionStatusDto getSessionStatus() {
        return SessionStatusDto.fromDomain(currentSession);
    }

    @Override
    public List<AccessRecordDto> getTodayRecords() {
        return todayRecords.stream()
                .map(AccessRecordDto::fromDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<AccessRecordDto> getLatestRecords(int limit) {
        int size = todayRecords.size();
        int fromIndex = Math.max(0, size - limit);

        List<AccessRecord> latest = new ArrayList<>(todayRecords.subList(fromIndex, size));
        Collections.reverse(latest); // Más recientes primero

        return latest.stream()
                .map(AccessRecordDto::fromDomain)
                .collect(Collectors.toList());
    }

    /**
     * Verifica si el UID corresponde al administrador.
     */
    private boolean isAdminUid(String uid) {
        return uid != null && adminUid != null
                && uid.toUpperCase().trim().equals(adminUid.toUpperCase().trim());
    }

    @Override
    public void processIncomingUid(String uid) {
        log.info("Procesando UID: {}", uid);

        // IMPORTANTE: Verificar si estamos en modo de espera del admin
        if (deviceManagementService != null && deviceManagementService.isWaitingForAdmin()) {
            log.info("Modo ESPERANDO_ADMIN activo - validando tarjeta del administrador");
            deviceManagementService.validateAdminUid(uid);
            return;
        }

        // IMPORTANTE: Verificar si estamos en modo enrolamiento
        if (deviceManagementService != null && deviceManagementService.isEnrollmentMode()) {
            log.info("Modo enrolamiento activo - capturando UID para registro");
            deviceManagementService.captureUidForEnrollment(uid);
            return;
        }

        // === OPERACIONES RAPIDAS (en el hilo del lector serial) ===

        boolean isAdmin = isAdminUid(uid);
        boolean isRegistered = isAdmin || userRegistryPort.existsByUid(uid);
        log.info("UID '{}': registrado={}, esAdmin={}", uid, isRegistered, isAdmin);

        // CRITICO: Enviar respuesta al Arduino INMEDIATAMENTE
        try {
            char response = isRegistered ? '1' : '0';
            serialListener.sendCommand(response);
            log.info("Respuesta enviada al Arduino: {}", response);
        } catch (Exception e) {
            log.error("Error enviando respuesta al Arduino: {}", e.getMessage());
        }

        // === OPERACIONES LENTAS (en hilo background) ===
        // El hilo del lector serial queda libre para seguir leyendo.

        final boolean finalIsAdmin = isAdmin;
        final boolean finalIsRegistered = isRegistered;

        CompletableFuture.runAsync(() -> {
            try {
                String userName;
                if (finalIsAdmin) {
                    userName = "Administrador";
                } else {
                    userName = userRegistryPort.findByUid(uid)
                            .map(user -> user.getName())
                            .orElse("No registrado");
                }

                AccessStatus status = finalIsRegistered ? AccessStatus.GRANTED : AccessStatus.DENIED;

                AccessRecord record = AccessRecord.builder()
                        .uid(uid)
                        .timestamp(LocalDateTime.now())
                        .status(status)
                        .stationId(stationId)
                        .userName(userName)
                        .build();

                // Escribir a CSV
                try {
                    if (logWriter.isReady()) {
                        logWriter.write(record);
                    }
                } catch (Exception e) {
                    log.error("Error escribiendo CSV: {}", e.getMessage());
                }

                // Cache en memoria
                todayRecords.add(record);

                // Contador de sesión
                if (currentSession != null && currentSession.isActive()) {
                    currentSession.incrementRecordCount();
                }

                // WebSocket
                try {
                    AccessRecordDto dto = AccessRecordDto.fromDomain(record);
                    webSocketHandler.broadcastRecord(dto);
                } catch (Exception e) {
                    log.error("Error notificando WebSocket: {}", e.getMessage());
                }

                log.info("UID procesado: {} - {} - {}", uid, status, userName);

            } catch (Exception e) {
                log.error("Error en procesamiento async de UID: {}", e.getMessage(), e);
            }
        });
    }

    @Override
    public DayStatsDto getDayStats() {
        long total = todayRecords.size();
        long granted = todayRecords.stream()
                .filter(r -> r.getStatus() == AccessStatus.GRANTED)
                .count();
        long denied = total - granted;

        String lastAccess = todayRecords.isEmpty()
                ? "--:--:--"
                : todayRecords.get(todayRecords.size() - 1)
                        .getTimestamp()
                        .format(TIME_FORMAT);

        return new DayStatsDto(total, granted, denied, lastAccess);
    }
}
