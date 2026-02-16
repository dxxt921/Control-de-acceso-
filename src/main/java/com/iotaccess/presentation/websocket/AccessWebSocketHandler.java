package com.iotaccess.presentation.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotaccess.application.dto.AccessRecordDto;
import com.iotaccess.application.dto.RegisteredUserDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Handler de WebSocket para enviar actualizaciones en tiempo real a los
 * clientes.
 */
@Component
@Slf4j
public class AccessWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("Nueva conexión WebSocket: {} (Total: {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Conexión WebSocket cerrada: {} (Restantes: {})", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.debug("Mensaje recibido de {}: {}", session.getId(), message.getPayload());
        // Por ahora solo logueamos, podríamos implementar ping/pong aquí
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Error en WebSocket {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    /**
     * Envía un registro de acceso a todos los clientes conectados.
     * 
     * @param record DTO del registro a enviar
     */
    public void broadcastRecord(AccessRecordDto record) {
        if (sessions.isEmpty()) {
            log.debug("No hay clientes WebSocket conectados");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(new WebSocketMessage("NEW_RECORD", record));
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.error("Error enviando a sesión {}: {}", session.getId(), e.getMessage());
                        sessions.remove(session);
                    }
                }
            }

            log.debug("Broadcast enviado a {} clientes", sessions.size());

        } catch (Exception e) {
            log.error("Error creando mensaje JSON: {}", e.getMessage());
        }
    }

    /**
     * Envía un mensaje de estado de sesión a todos los clientes.
     * 
     * @param active  Estado de la sesión
     * @param message Mensaje descriptivo
     */
    public void broadcastSessionStatus(boolean active, String message) {
        try {
            String json = objectMapper.writeValueAsString(
                    new WebSocketMessage("SESSION_STATUS", new SessionStatus(active, message)));
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        sessions.remove(session);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error enviando status: {}", e.getMessage());
        }
    }

    /**
     * Notifica el cambio de modo de enrolamiento a todos los clientes.
     * 
     * @param active           Si el modo enrolamiento está activo
     * @param secondsRemaining Segundos restantes antes del timeout
     * @param capturedUid      UID capturado (puede ser null)
     */
    public void broadcastEnrollmentMode(boolean active, int secondsRemaining, String capturedUid) {
        broadcast("ENROLLMENT_MODE", new EnrollmentModeData(active, secondsRemaining, capturedUid));
    }

    /**
     * Notifica un UID capturado durante el enrolamiento.
     * 
     * @param uid UID del dispositivo detectado
     */
    public void broadcastCapturedUid(String uid) {
        broadcast("UID_CAPTURED", new CapturedUidData(uid));
    }

    /**
     * Notifica la finalización exitosa de un enrolamiento.
     * 
     * @param user Datos del usuario registrado
     */
    public void broadcastEnrollmentComplete(RegisteredUserDto user) {
        broadcast("ENROLLMENT_COMPLETE", user);
    }

    /**
     * Notifica un error durante el enrolamiento.
     * 
     * @param errorMessage Mensaje de error
     */
    public void broadcastEnrollmentError(String errorMessage) {
        broadcast("ENROLLMENT_ERROR", new ErrorData(errorMessage));
    }

    /**
     * Notifica la eliminación de un usuario.
     * 
     * @param uid UID del usuario eliminado
     */
    public void broadcastUserDeleted(String uid) {
        broadcast("USER_DELETED", new CapturedUidData(uid));
    }

    /**
     * Notifica que se requiere validación del administrador.
     * 
     * @param secondsRemaining Segundos restantes para validar
     */
    public void broadcastAdminRequired(int secondsRemaining) {
        broadcast("ADMIN_REQUIRED", new AdminRequiredData(secondsRemaining));
    }

    /**
     * Notifica que el administrador fue validado correctamente.
     */
    public void broadcastAdminApproved() {
        broadcast("ADMIN_APPROVED", new AdminApprovedData(true, "Administrador validado correctamente"));
    }

    /**
     * Notifica que la tarjeta no corresponde al administrador.
     * 
     * @param message Mensaje de error
     */
    public void broadcastAdminRejected(String message) {
        broadcast("ADMIN_REJECTED", new ErrorData(message));
    }

    /**
     * Notifica que el proceso batch ha iniciado.
     */
    public void broadcastBatchStarted() {
        broadcast("BATCH_STARTED", new BatchStartedData(java.time.LocalDateTime.now().toString()));
    }

    /**
     * Notifica que el proceso batch ha finalizado.
     *
     * @param recordsProcessed Registros procesados
     * @param errors           Número de errores
     * @param success          Si fue exitoso
     */
    public void broadcastBatchCompleted(int recordsProcessed, int errors, boolean success) {
        broadcast("BATCH_COMPLETED",
                new BatchCompletedData(recordsProcessed, errors, success, java.time.LocalDateTime.now().toString()));
    }

    /**
     * Método genérico para broadcast de mensajes.
     */
    private void broadcast(String type, Object data) {
        if (sessions.isEmpty()) {
            log.debug("No hay clientes WebSocket conectados");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(new WebSocketMessage(type, data));
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.error("Error enviando a sesión {}: {}", session.getId(), e.getMessage());
                        sessions.remove(session);
                    }
                }
            }

            log.debug("Broadcast {} enviado a {} clientes", type, sessions.size());

        } catch (Exception e) {
            log.error("Error creando mensaje JSON: {}", e.getMessage());
        }
    }

    /**
     * Obtiene el número de clientes conectados.
     */
    public int getConnectedClients() {
        return sessions.size();
    }

    // Records para mensajes
    record WebSocketMessage(String type, Object data) {
    }

    record SessionStatus(boolean active, String message) {
    }

    record EnrollmentModeData(boolean active, int secondsRemaining, String capturedUid) {
    }

    record CapturedUidData(String uid) {
    }

    record ErrorData(String message) {
    }

    record AdminRequiredData(int secondsRemaining) {
    }

    record AdminApprovedData(boolean approved, String message) {
    }

    record BatchStartedData(String startTime) {
    }

    record BatchCompletedData(int recordsProcessed, int errors, boolean success, String completedTime) {
    }
}
