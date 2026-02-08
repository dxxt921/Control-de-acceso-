package com.iotaccess.presentation.controller;

import com.iotaccess.application.dto.AccessRecordDto;
import com.iotaccess.application.dto.SessionStatusDto;
import com.iotaccess.application.scheduler.BatchProcessingJob;
import com.iotaccess.application.service.AccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador para el dashboard de monitoreo en tiempo real.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final AccessService accessService;
    private final BatchProcessingJob batchProcessingJob;

    /**
     * Página del dashboard.
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        SessionStatusDto status = accessService.getSessionStatus();

        // Si no hay sesión activa, redirigir a configuración
        if (!status.isActive()) {
            return "redirect:/";
        }

        List<AccessRecordDto> records = accessService.getLatestRecords(50);
        AccessService.DayStatsDto stats = accessService.getDayStats();

        model.addAttribute("sessionStatus", status);
        model.addAttribute("records", records);
        model.addAttribute("stats", stats);

        return "dashboard";
    }

    /**
     * API: Obtiene los últimos registros.
     */
    @GetMapping("/api/records/latest")
    @ResponseBody
    public ResponseEntity<List<AccessRecordDto>> getLatestRecords(
            @RequestParam(defaultValue = "50") int limit) {

        List<AccessRecordDto> records = accessService.getLatestRecords(limit);
        return ResponseEntity.ok(records);
    }

    /**
     * API: Obtiene todos los registros del día.
     */
    @GetMapping("/api/records/today")
    @ResponseBody
    public ResponseEntity<List<AccessRecordDto>> getTodayRecords() {
        List<AccessRecordDto> records = accessService.getTodayRecords();
        return ResponseEntity.ok(records);
    }

    /**
     * API: Obtiene estadísticas del día.
     */
    @GetMapping("/api/stats")
    @ResponseBody
    public ResponseEntity<AccessService.DayStatsDto> getDayStats() {
        return ResponseEntity.ok(accessService.getDayStats());
    }

    /**
     * API: Ejecuta el proceso batch manualmente.
     */
    @PostMapping("/api/batch/run")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> runBatch() {
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Ejecutando proceso batch manualmente...");
            int processed = batchProcessingJob.runManualBatch();

            response.put("success", true);
            response.put("message", "Proceso batch completado");
            response.put("recordsProcessed", processed);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error en batch manual: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API: Simula un acceso para testing.
     */
    @PostMapping("/api/test/access")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> simulateAccess(
            @RequestParam(defaultValue = "4A B1 C2 33") String uid) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Simulando acceso con UID: {}", uid);
            accessService.processIncomingUid(uid);

            response.put("success", true);
            response.put("message", "Acceso simulado correctamente");
            response.put("uid", uid);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error simulando acceso: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
