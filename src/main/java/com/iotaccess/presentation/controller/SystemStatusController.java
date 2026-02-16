package com.iotaccess.presentation.controller;

import com.iotaccess.application.scheduler.BatchProcessingJob;
import com.iotaccess.application.service.AccessService;
import com.iotaccess.application.service.DeviceManagementService;
import com.iotaccess.domain.port.UserRegistryPort;
import com.iotaccess.infrastructure.serial.SerialListener;
import com.iotaccess.presentation.websocket.AccessWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controlador REST para consultar el estado completo del sistema.
 * Diseñado para uso con Postman y para alimentar el panel de estado del
 * dashboard.
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemStatusController {

    private final SerialListener serialListener;
    private final BatchProcessingJob batchProcessingJob;
    private final AccessService accessService;
    private final DeviceManagementService deviceManagementService;
    private final AccessWebSocketHandler webSocketHandler;
    private final UserRegistryPort userRegistryPort;

    @Value("${operation.start-hour:8}")
    private int operationStartHour;

    @Value("${operation.end-hour:22}")
    private int operationEndHour;

    /**
     * GET /api/system/status
     * Devuelve el estado completo del sistema en un solo JSON.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        // Serial Listener
        Map<String, Object> serial = new LinkedHashMap<>();
        serial.put("running", serialListener.isRunning());
        serial.put("port", serialListener.getCurrentPortName());
        status.put("serialListener", serial);

        // Batch Processing
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("cronExpression", batchProcessingJob.getCurrentCronExpression());
        batch.put("scheduledTime", batchProcessingJob.getScheduledTimeDisplay());
        batch.put("lastRunTime", batchProcessingJob.getLastRunTime() != null
                ? batchProcessingJob.getLastRunTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null);
        batch.put("lastRunRecords", batchProcessingJob.getLastRunRecords());
        batch.put("lastRunSuccess", batchProcessingJob.isLastRunSuccess());
        batch.put("lastRunError", batchProcessingJob.getLastRunError());
        status.put("batchProcessing", batch);

        // Session
        var sessionStatus = accessService.getSessionStatus();
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("active", sessionStatus.isActive());
        session.put("sessionName", sessionStatus.getSessionName());
        session.put("portName", sessionStatus.getPortName());
        session.put("recordCount", sessionStatus.getRecordCount());
        session.put("startTime", sessionStatus.getStartTime());
        status.put("session", session);

        // System Mode
        status.put("systemMode", deviceManagementService.getCurrentMode().name());

        // Users & Clients
        status.put("registeredUsers", userRegistryPort.findAll().size());
        status.put("connectedWebSocketClients", webSocketHandler.getConnectedClients());

        // Operation Hours
        Map<String, Object> opHours = new LinkedHashMap<>();
        opHours.put("start", operationStartHour);
        opHours.put("end", operationEndHour);
        status.put("operationHours", opHours);
        status.put("withinOperationHours", batchProcessingJob.isWithinOperationHours());

        // Day Stats
        var dayStats = accessService.getDayStats();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAccesses", dayStats.totalAccesses());
        stats.put("grantedCount", dayStats.grantedCount());
        stats.put("deniedCount", dayStats.deniedCount());
        stats.put("lastAccessTime", dayStats.lastAccessTime());
        status.put("todayStats", stats);

        // Server time
        status.put("serverTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        return ResponseEntity.ok(status);
    }

    @Value("${admin.uid:EB-EE-C0-1}")
    private String adminUid;

    /**
     * POST /api/system/batch/schedule
     * Reprograma la hora del batch. Requiere el UID del administrador para
     * autorizar.
     *
     * @param hour     Hora (0-23)
     * @param minute   Minuto (0-59)
     * @param adminUid UID del administrador para autorizar el cambio
     */
    @PostMapping("/batch/schedule")
    public ResponseEntity<Map<String, Object>> rescheduleBatch(
            @RequestParam int hour,
            @RequestParam(defaultValue = "0") int minute,
            @RequestParam String adminUid) {
        Map<String, Object> response = new LinkedHashMap<>();

        // Validar UID del administrador
        if (!this.adminUid.equalsIgnoreCase(adminUid.trim())) {
            response.put("success", false);
            response.put("message", "UID de administrador inválido. Autorización denegada.");
            return ResponseEntity.status(403).body(response);
        }

        try {
            String newCron = batchProcessingJob.reschedule(hour, minute);
            response.put("success", true);
            response.put("message", String.format("Batch reprogramado a las %02d:%02d", hour, minute));
            response.put("cronExpression", newCron);
            response.put("scheduledTime", batchProcessingJob.getScheduledTimeDisplay());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
