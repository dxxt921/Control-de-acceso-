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
    public void processIncomingUid(String uid) {
        log.info("Procesando UID: {}", uid);

        // IMPORTANTE: Verificar si estamos en modo enrolamiento
        if (deviceManagementService != null && deviceManagementService.isEnrollmentMode()) {
            log.info("Modo enrolamiento activo - capturando UID para registro");
            deviceManagementService.captureUidForEnrollment(uid);
            return; // No procesar como acceso normal
        }

        // Verificar SOLO en el registro local (user_registry.csv)
        // La base de datos NO se usa para validación de acceso, solo para
        // sincronización batch
        boolean isRegistered = userRegistryPort.existsByUid(uid);
        log.info("Verificación LOCAL para UID '{}': {}", uid, isRegistered);

        String userName = userRegistryPort.findByUid(uid)
                .map(user -> user.getName())
                .orElse("No registrado");

        AccessStatus status = isRegistered ? AccessStatus.GRANTED : AccessStatus.DENIED;

        // IMPORTANTE: Enviar respuesta al Arduino ('1' = permitido, '0' = denegado)
        try {
            char response = isRegistered ? '1' : '0';
            serialListener.sendCommand(response);
            log.info("Respuesta enviada al Arduino: {}", response);
        } catch (Exception e) {
            log.error("Error enviando respuesta al Arduino: {}", e.getMessage());
        }

        // Crear registro
        AccessRecord record = AccessRecord.builder()
                .uid(uid)
                .timestamp(LocalDateTime.now())
                .status(status)
                .stationId(stationId)
                .userName(userName)
                .build();

        // Escribir a CSV
        if (logWriter.isReady()) {
            logWriter.write(record);
        }

        // Agregar a cache en memoria
        todayRecords.add(record);

        // Actualizar contador de sesión
        if (currentSession != null && currentSession.isActive()) {
            currentSession.incrementRecordCount();
        }

        // Notificar a clientes WebSocket
        try {
            AccessRecordDto dto = AccessRecordDto.fromDomain(record);
            webSocketHandler.broadcastRecord(dto);
        } catch (Exception e) {
            log.error("Error notificando WebSocket: {}", e.getMessage());
        }

        log.info("UID procesado: {} - {} - {}", uid, status, userName);
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
