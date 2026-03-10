package com.iotaccess.infrastructure.services;

import com.iotaccess.application.dtos.AccessRecordDto;
import com.iotaccess.application.dtos.DayStatsDto;
import com.iotaccess.application.dtos.SessionStatusDto;
import com.iotaccess.application.services.AccessService;
import com.iotaccess.application.services.DeviceManagementService;
import com.iotaccess.domain.model.AccessRecord;
import com.iotaccess.domain.model.AccessStatus;
import com.iotaccess.domain.model.CaptureSession;
import com.iotaccess.domain.model.SerialPortInfo;
import com.iotaccess.utility.ports.AccessLogWriter;
import com.iotaccess.utility.ports.UserRegistryPort;
import com.iotaccess.infrastructure.serials.SerialListener;
import com.iotaccess.infrastructure.serials.SerialPortScanner;
import com.iotaccess.presentation.websockets.AccessWebSocketHandler;
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

    @Value("${csv.data-logs-path:data_logs}")
    private String dataLogsPath;

    // Almacenamiento en memoria de la sesión actual
    private CaptureSession currentSession;

    // Cache de registros del día para la UI (thread-safe)
    private final CopyOnWriteArrayList<AccessRecord> todayRecords = new CopyOnWriteArrayList<>();

    public AccessServiceImpl(
            SerialPortScanner portScanner,
            SerialListener serialListener,
            AccessLogWriter logWriter,
            AccessWebSocketHandler webSocketHandler,
            UserRegistryPort userRegistryPort) {
        this.portScanner = portScanner;
        this.serialListener = serialListener;
        this.logWriter = logWriter;
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
        reconnectSession(portName, null);
    }

    @Override
    public synchronized void reconnectSession(String portName, String sessionFile) {
        // Si hay sesión activa, solo detener el serial (no el CSV)
        if (currentSession != null && currentSession.isActive()) {
            serialListener.stop();
        }

        // Si no se proporcionó un archivo, verificar si hay uno activo
        if (sessionFile == null || sessionFile.trim().isEmpty()) {
            if (!logWriter.isReady()) {
                throw new IllegalStateException(
                        "No hay sesión CSV previa y no se especificó un archivo. Use 'Iniciar sesión' en su lugar.");
            }
        } else {
            // Se proporcionó un archivo, inicializar logWriter con ese archivo
            logWriter.initializeWithExistingFile(sessionFile.trim());
        }

        log.info("⟳ Reconectando Arduino en puerto={}, continuando CSV {}", portName,
                sessionFile != null ? sessionFile : "existente activo");

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
                    ((com.iotaccess.infrastructure.files.CsvAccessLogWriter) logWriter)
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

        // Registrar el cambio de nombre
        try {
            java.nio.file.Path historyPath = java.nio.file.Paths.get(dataLogsPath, "rename_history.csv");
            if (!java.nio.file.Files.exists(historyPath)) {
                java.nio.file.Files.createDirectories(historyPath.getParent());
                java.nio.file.Files.writeString(historyPath, "timestamp,old_name,new_name\n",
                        java.nio.file.StandardOpenOption.CREATE);
            }
            String record = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + ","
                    + currentSession.getSessionName() + "," + newName + "\n";
            java.nio.file.Files.writeString(historyPath, record, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("Error al registrar historial de renombramiento: {}", e.getMessage());
        }

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

    @Override
    public List<String> getRecentSessions() {
        try {
            java.nio.file.Path logsDir = java.nio.file.Paths.get("data_logs");
            if (!java.nio.file.Files.exists(logsDir)) {
                return Collections.emptyList();
            }

            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(logsDir)) {
                return files
                        .filter(path -> path.toString().endsWith(".csv"))
                        .filter(java.nio.file.Files::isRegularFile)
                        .filter(path -> !path.getFileName().toString().startsWith("user_registry"))
                        .filter(path -> !path.getFileName().toString().startsWith("rename_history"))
                        .filter(path -> !path.getFileName().toString().endsWith("_backup.csv"))
                        .sorted(java.util.Comparator.comparingLong((java.nio.file.Path p) -> p.toFile().lastModified())
                                .reversed())
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.toList());
            }
        } catch (java.io.IOException e) {
            log.error("Error obteniendo sesiones recientes: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<java.util.Map<String, String>> getRenameHistory() {
        try {
            java.nio.file.Path historyPath = java.nio.file.Paths.get(dataLogsPath, "rename_history.csv");
            if (!java.nio.file.Files.exists(historyPath)) {
                return Collections.emptyList();
            }

            List<String> lines = java.nio.file.Files.readAllLines(historyPath);
            if (lines.size() <= 1)
                return Collections.emptyList(); // Solo header

            List<java.util.Map<String, String>> history = new ArrayList<>();
            // Empezar en index 1 (skip header) y procesar en orden inverso (más recientes
            // primero)
            for (int i = lines.size() - 1; i >= 1; i--) {
                String[] parts = lines.get(i).split(",");
                if (parts.length >= 3) {
                    java.util.Map<String, String> entry = new java.util.HashMap<>();
                    entry.put("timestamp", parts[0]);
                    entry.put("oldName", parts[1]);
                    entry.put("newName", parts[2]);
                    history.add(entry);
                }
            }
            return history;
        } catch (Exception e) {
            log.error("Error obteniendo historial de nombres: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Normaliza un UID eliminando separadores (guiones, espacios, dos puntos)
     * para comparación robusta.
     */
    private String stripUidSeparators(String uid) {
        if (uid == null)
            return "";
        return uid.toUpperCase().trim().replaceAll("[\\-\\s:]+", "");
    }

    /**
     * Verifica si el UID corresponde al administrador.
     * Compara solo dígitos hex, ignorando separadores.
     */
    private boolean isAdminUid(String uid) {
        String strippedUid = stripUidSeparators(uid);
        String strippedAdmin = stripUidSeparators(adminUid);

        // DEBUG: Comparación detallada
        log.info(">>> isAdminUid comparación:");
        log.info("    UID recibido (original): [{}]", uid);
        log.info("    UID recibido (stripped):  [{}] (len={})", strippedUid, strippedUid.length());
        log.info("    Admin config (original): [{}]", adminUid);
        log.info("    Admin config (stripped):  [{}] (len={})", strippedAdmin, strippedAdmin.length());
        log.info("    ¿Son iguales?: {}", strippedUid.equals(strippedAdmin));

        return uid != null && adminUid != null
                && strippedUid.equals(strippedAdmin);
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

        boolean isAdmin = false;
        boolean isRegistered = false;

        try {
            isAdmin = isAdminUid(uid);
            isRegistered = isAdmin || userRegistryPort.existsByUid(uid);
            log.info("UID '{}': registrado={}, esAdmin={}", uid, isRegistered, isAdmin);
        } catch (Exception e) {
            log.error("Error validando usuario, asumiendo NO REGISTRADO: {}", e.getMessage());
        }

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
