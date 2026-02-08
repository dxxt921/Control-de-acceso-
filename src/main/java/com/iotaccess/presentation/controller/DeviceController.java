package com.iotaccess.presentation.controller;

import com.iotaccess.application.dto.EnrollmentStateDto;
import com.iotaccess.application.dto.RegisteredUserDto;
import com.iotaccess.application.service.DeviceManagementService;
import com.iotaccess.application.service.IdentityService;
import com.iotaccess.presentation.websocket.AccessWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST para gestión de dispositivos y enrolamiento.
 * Endpoints para listar usuarios, iniciar enrolamiento, y eliminar
 * dispositivos.
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {

    private final IdentityService identityService;
    private final DeviceManagementService deviceManagementService;
    private final AccessWebSocketHandler webSocketHandler;

    /**
     * Obtiene todos los usuarios registrados.
     * 
     * @return Lista de usuarios
     */
    @GetMapping
    public ResponseEntity<List<RegisteredUserDto>> getAllDevices() {
        log.info("Obteniendo lista de dispositivos registrados");
        List<RegisteredUserDto> users = identityService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    /**
     * Obtiene el conteo de usuarios registrados.
     * 
     * @return Conteo de usuarios
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getDeviceCount() {
        Map<String, Long> response = new HashMap<>();
        response.put("count", identityService.getUserCount());
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene el estado actual del modo de enrolamiento.
     * 
     * @return Estado del enrolamiento
     */
    @GetMapping("/enrollment/status")
    public ResponseEntity<EnrollmentStateDto> getEnrollmentStatus() {
        return ResponseEntity.ok(deviceManagementService.getEnrollmentState());
    }

    /**
     * Inicia el modo de enrolamiento por 20 segundos.
     * 
     * @return Estado inicial del enrolamiento
     */
    @PostMapping("/enrollment/start")
    public ResponseEntity<Map<String, Object>> startEnrollment() {
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Iniciando modo de enrolamiento...");
            EnrollmentStateDto state = deviceManagementService.startEnrollmentMode();

            response.put("success", true);
            response.put("message", "Modo enrolamiento iniciado");
            response.put("state", state);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error iniciando enrolamiento: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Confirma el enrolamiento de un dispositivo con su nombre.
     * 
     * @param request Objeto con uid y name
     * @return Usuario registrado
     */
    @PostMapping("/enrollment/confirm")
    public ResponseEntity<Map<String, Object>> confirmEnrollment(@RequestBody EnrollmentConfirmRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Confirmando enrolamiento: {} - {}", request.uid(), request.name());

            if (request.uid() == null || request.uid().isBlank()) {
                throw new IllegalArgumentException("UID es requerido");
            }
            if (request.name() == null || request.name().isBlank()) {
                throw new IllegalArgumentException("Nombre es requerido");
            }

            RegisteredUserDto user = deviceManagementService.confirmEnrollment(request.uid(), request.name());

            response.put("success", true);
            response.put("message", "Dispositivo registrado exitosamente");
            response.put("user", user);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error confirmando enrolamiento: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Cancela el modo de enrolamiento.
     * 
     * @return Resultado de la operación
     */
    @PostMapping("/enrollment/cancel")
    public ResponseEntity<Map<String, Object>> cancelEnrollment() {
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Cancelando enrolamiento...");
            deviceManagementService.cancelEnrollment();

            response.put("success", true);
            response.put("message", "Enrolamiento cancelado");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error cancelando enrolamiento: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Elimina un dispositivo por su UID.
     * 
     * @param uid UID del dispositivo a eliminar (URL encoded)
     * @return Resultado de la operación
     */
    @DeleteMapping("/{uid}")
    public ResponseEntity<Map<String, Object>> deleteDevice(@PathVariable String uid) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Decodificar el UID (puede venir con espacios como %20)
            String decodedUid = java.net.URLDecoder.decode(uid, java.nio.charset.StandardCharsets.UTF_8);
            log.info("Eliminando dispositivo: {}", decodedUid);

            boolean deleted = identityService.deleteUser(decodedUid);

            if (deleted) {
                // Notificar via WebSocket
                webSocketHandler.broadcastUserDeleted(decodedUid);

                response.put("success", true);
                response.put("message", "Dispositivo eliminado");
                response.put("uid", decodedUid);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Dispositivo no encontrado");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("Error eliminando dispositivo: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Request DTO para confirmar enrolamiento.
     */
    record EnrollmentConfirmRequest(String uid, String name) {
    }
}
