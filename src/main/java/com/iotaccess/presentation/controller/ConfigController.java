package com.iotaccess.presentation.controller;

import com.iotaccess.application.dto.SessionStatusDto;
import com.iotaccess.application.service.AccessService;
import com.iotaccess.domain.model.SerialPortInfo;
import com.iotaccess.infrastructure.serial.SerialPortScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador para la página de configuración inicial.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ConfigController {

    private final AccessService accessService;
    private final SerialPortScanner portScanner;

    /**
     * Redirección a la página principal (dashboard).
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    /**
     * API: Obtiene la lista de puertos disponibles.
     */
    @GetMapping("/api/ports")
    @ResponseBody
    public ResponseEntity<List<SerialPortInfo>> getPorts() {
        List<SerialPortInfo> ports = accessService.getAvailablePorts();
        log.info("Puertos encontrados: {}", ports.size());
        return ResponseEntity.ok(ports);
    }

    /**
     * API: Refresca la lista de puertos.
     */
    @PostMapping("/api/ports/refresh")
    @ResponseBody
    public ResponseEntity<List<SerialPortInfo>> refreshPorts() {
        List<SerialPortInfo> ports = accessService.getAvailablePorts();
        return ResponseEntity.ok(ports);
    }

    /**
     * API: Inicia una sesión de captura.
     */
    @PostMapping("/api/session/start")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startSession(
            @RequestParam String portName,
            @RequestParam String sessionName) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Solicitud para iniciar sesión: puerto={}, nombre={}", portName, sessionName);

            // Validaciones básicas
            if (portName == null || portName.isBlank()) {
                response.put("success", false);
                response.put("message", "Debe seleccionar un puerto");
                return ResponseEntity.badRequest().body(response);
            }

            if (sessionName == null || sessionName.isBlank()) {
                sessionName = "sesion_" + System.currentTimeMillis();
            }

            // Limpiar nombre de sesión
            sessionName = sessionName.replaceAll("[^a-zA-Z0-9_-]", "_");

            accessService.startSession(portName, sessionName);

            response.put("success", true);
            response.put("message", "Sesión iniciada correctamente");
            response.put("redirectUrl", "/dashboard");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error iniciando sesión: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API: Detiene la sesión actual.
     */
    @PostMapping("/api/session/stop")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> stopSession() {
        Map<String, Object> response = new HashMap<>();

        try {
            accessService.stopSession();
            response.put("success", true);
            response.put("message", "Sesión detenida correctamente");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error deteniendo sesión: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API: Obtiene el estado de la sesión actual.
     */
    @GetMapping("/api/session/status")
    @ResponseBody
    public ResponseEntity<SessionStatusDto> getSessionStatus() {
        return ResponseEntity.ok(accessService.getSessionStatus());
    }

    /**
     * API: Prueba de conexión con un puerto serial.
     * Envía PING y espera PONG del firmware de control de acceso.
     */
    @PostMapping("/api/ports/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testPort(@RequestParam String portName) {
        log.info("Probando conexión en puerto: {}", portName);
        Map<String, Object> result = portScanner.probePort(portName);
        return ResponseEntity.ok(result);
    }
}
