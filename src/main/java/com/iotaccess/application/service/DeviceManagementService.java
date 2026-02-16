package com.iotaccess.application.service;

import com.iotaccess.application.dto.EnrollmentStateDto;
import com.iotaccess.application.dto.RegisteredUserDto;
import com.iotaccess.domain.model.SystemMode;
import com.iotaccess.infrastructure.serial.SerialListener;
import com.iotaccess.presentation.websocket.AccessWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio para gestión del modo de enrolamiento de dispositivos.
 * Maneja el estado global del sistema (ACCESO/ESPERANDO_ADMIN/ENROLAMIENTO)
 * y la lógica de captura de UIDs.
 * 
 * FLUJO DE SEGURIDAD:
 * 1. Usuario solicita enrolamiento -> Sistema pide tarjeta del admin
 * 2. Admin acerca su tarjeta -> Si es válida, pasa a modo enrolamiento
 * 3. Usuario acerca nueva tarjeta -> Se registra
 */
@Service
@Slf4j
public class DeviceManagementService {

    private static final int ENROLLMENT_TIMEOUT_SECONDS = 20;
    private static final int ADMIN_VALIDATION_TIMEOUT_SECONDS = 15;

    private final IdentityService identityService;
    private final SerialListener serialListener;
    private final AccessWebSocketHandler webSocketHandler;

    // UID del administrador desde configuración
    @Value("${admin.uid:EB-EE-C0-1}")
    private String adminUid;

    // Estado global del sistema
    private final AtomicReference<SystemMode> currentMode = new AtomicReference<>(SystemMode.ACCESO);

    // UID capturado pendiente de confirmación
    private final AtomicReference<String> capturedUid = new AtomicReference<>(null);

    // Contador regresivo
    private final AtomicInteger secondsRemaining = new AtomicInteger(0);

    // Scheduler para timeout y countdown
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> timeoutTask;
    private ScheduledFuture<?> countdownTask;

    public DeviceManagementService(IdentityService identityService,
            SerialListener serialListener,
            AccessWebSocketHandler webSocketHandler) {
        this.identityService = identityService;
        this.serialListener = serialListener;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Inicia el proceso de enrolamiento solicitando primero la validación del
     * admin.
     * NUEVO FLUJO: Ahora requiere que el admin valide su tarjeta primero.
     * 
     * @return Estado inicial del enrolamiento (esperando admin)
     */
    public EnrollmentStateDto startEnrollmentMode() {
        log.info("Iniciando proceso de enrolamiento - Esperando validación del admin...");

        // Limpiar estado anterior
        capturedUid.set(null);
        secondsRemaining.set(ADMIN_VALIDATION_TIMEOUT_SECONDS);

        // Cambiar a modo ESPERANDO_ADMIN
        currentMode.set(SystemMode.ESPERANDO_ADMIN);

        // Enviar comando 'W' al Arduino (Wait for Admin)
        sendSerialCommand('W');

        // Notificar via WebSocket que se requiere admin
        webSocketHandler.broadcastAdminRequired(ADMIN_VALIDATION_TIMEOUT_SECONDS);

        // Iniciar countdown cada segundo
        startCountdown(ADMIN_VALIDATION_TIMEOUT_SECONDS);

        // Programar timeout
        timeoutTask = scheduler.schedule(() -> {
            log.info("Timeout de validación de admin alcanzado");
            cancelEnrollment();
        }, ADMIN_VALIDATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("Esperando tarjeta del administrador...");

        return EnrollmentStateDto.waitingAdmin(ADMIN_VALIDATION_TIMEOUT_SECONDS);
    }

    /**
     * Valida si el UID recibido corresponde al administrador.
     * Si es válido, pasa a modo ENROLAMIENTO.
     * Si no es válido, rechaza y vuelve a modo ACCESO.
     * 
     * @param uid UID de la tarjeta escaneada
     * @return true si el admin fue validado correctamente
     */
    public boolean validateAdminUid(String uid) {
        if (currentMode.get() != SystemMode.ESPERANDO_ADMIN) {
            log.warn("Intento de validar admin fuera de modo ESPERANDO_ADMIN");
            return false;
        }

        String normalizedUid = uid.toUpperCase().trim();
        String normalizedAdminUid = adminUid.toUpperCase().trim();

        log.info("Validando UID del admin. Recibido: {}, Esperado: {}", normalizedUid, normalizedAdminUid);

        if (normalizedUid.equals(normalizedAdminUid)) {
            log.info("¡Admin validado correctamente! Pasando a modo enrolamiento...");

            // Cancelar tareas actuales
            cancelScheduledTasks();

            // Cambiar a modo ENROLAMIENTO
            currentMode.set(SystemMode.ENROLAMIENTO);
            secondsRemaining.set(ENROLLMENT_TIMEOUT_SECONDS);

            // Enviar comando 'E' al Arduino
            sendSerialCommand('E');

            // Notificar via WebSocket
            webSocketHandler.broadcastAdminApproved();
            webSocketHandler.broadcastEnrollmentMode(true, ENROLLMENT_TIMEOUT_SECONDS, null);

            // Iniciar nuevo countdown para enrolamiento
            startCountdown(ENROLLMENT_TIMEOUT_SECONDS);

            // Programar nuevo timeout
            timeoutTask = scheduler.schedule(() -> {
                log.info("Timeout de enrolamiento alcanzado");
                cancelEnrollment();
            }, ENROLLMENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return true;
        } else {
            log.warn("UID no corresponde al administrador. Acceso denegado.");

            // Enviar comando 'X' al Arduino (Admin rejected)
            sendSerialCommand('X');

            // Notificar via WebSocket
            webSocketHandler.broadcastAdminRejected("La tarjeta no corresponde al administrador");

            // Volver a modo acceso
            endEnrollmentMode();

            return false;
        }
    }

    /**
     * Captura un UID durante el modo de enrolamiento.
     * Llamado desde AccessService cuando se recibe un UID en modo ENROLAMIENTO.
     * 
     * @param uid UID capturado del dispositivo NFC
     */
    public void captureUidForEnrollment(String uid) {
        if (currentMode.get() != SystemMode.ENROLAMIENTO) {
            log.warn("Intento de captura fuera de modo enrolamiento");
            return;
        }

        log.info("UID capturado para enrolamiento: {}", uid);

        // Verificar si ya está registrado
        if (identityService.isUserRegistered(uid)) {
            log.warn("Dispositivo ya registrado: {}", uid);
            webSocketHandler.broadcastEnrollmentError("El dispositivo ya está registrado: " + uid);
            return;
        }

        // Verificar que no sea la tarjeta del admin
        if (uid.toUpperCase().trim().equals(adminUid.toUpperCase().trim())) {
            log.warn("No se puede registrar la tarjeta del administrador como usuario");
            webSocketHandler.broadcastEnrollmentError("No se puede registrar la tarjeta del administrador");
            return;
        }

        // Guardar UID capturado
        capturedUid.set(uid);

        // Notificar via WebSocket para abrir el formulario
        webSocketHandler.broadcastCapturedUid(uid);

        log.info("UID {} capturado, esperando confirmación con nombre...", uid);
    }

    /**
     * Confirma el enrolamiento de un dispositivo con su nombre.
     * 
     * @param uid  UID del dispositivo
     * @param name Nombre asignado por el administrador
     * @return DTO del usuario registrado
     */
    public RegisteredUserDto confirmEnrollment(String uid, String name) {
        log.info("Confirmando enrolamiento: {} - {}", uid, name);

        if (currentMode.get() != SystemMode.ENROLAMIENTO) {
            throw new IllegalStateException("El sistema no está en modo enrolamiento");
        }

        String captured = capturedUid.get();
        if (captured == null || !captured.equalsIgnoreCase(uid)) {
            throw new IllegalArgumentException("UID no coincide con el capturado");
        }

        // Registrar usuario
        RegisteredUserDto user = identityService.registerUser(uid, name);

        // Enviar comando 'K:nombre' al Arduino (confirmación con nombre)
        sendSerialMessage("K:" + name);

        // Notificar éxito via WebSocket
        webSocketHandler.broadcastEnrollmentComplete(user);

        // Volver a modo acceso
        endEnrollmentMode();

        log.info("Enrolamiento completado: {} - {}", uid, name);

        return user;
    }

    /**
     * Cancela el modo de enrolamiento o validación de admin.
     */
    public void cancelEnrollment() {
        log.info("Cancelando modo de enrolamiento/validación de admin");
        endEnrollmentMode();
        webSocketHandler.broadcastEnrollmentMode(false, 0, null);
    }

    /**
     * Inicia el countdown que notifica cada segundo.
     */
    private void startCountdown(int seconds) {
        cancelScheduledTasks();
        secondsRemaining.set(seconds);

        countdownTask = scheduler.scheduleAtFixedRate(() -> {
            int remaining = secondsRemaining.decrementAndGet();

            if (remaining > 0) {
                SystemMode mode = currentMode.get();
                if (mode == SystemMode.ESPERANDO_ADMIN) {
                    webSocketHandler.broadcastAdminRequired(remaining);
                } else if (mode == SystemMode.ENROLAMIENTO) {
                    webSocketHandler.broadcastEnrollmentMode(true, remaining, capturedUid.get());
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Cancela las tareas programadas (countdown y timeout).
     */
    private void cancelScheduledTasks() {
        if (timeoutTask != null && !timeoutTask.isDone()) {
            timeoutTask.cancel(false);
        }
        if (countdownTask != null && !countdownTask.isDone()) {
            countdownTask.cancel(false);
        }
    }

    /**
     * Finaliza el modo de enrolamiento y vuelve a modo acceso.
     */
    private void endEnrollmentMode() {
        // Cancelar tareas programadas
        cancelScheduledTasks();

        // Limpiar estado
        capturedUid.set(null);
        secondsRemaining.set(0);

        // Cambiar modo
        currentMode.set(SystemMode.ACCESO);

        // Enviar comando 'A' al Arduino
        sendSerialCommand('A');

        log.info("Modo acceso restaurado");
    }

    /**
     * Envía un comando de un carácter al Arduino via serial.
     * 
     * @param command Comando a enviar ('E', 'A', 'W', 'X')
     */
    private void sendSerialCommand(char command) {
        try {
            serialListener.sendCommand(command);
            log.debug("Comando '{}' enviado al Arduino", command);
        } catch (Exception e) {
            log.error("Error enviando comando '{}' al Arduino: {}", command, e.getMessage());
        }
    }

    /**
     * Envía un mensaje completo al Arduino via serial.
     * Usado para enviar datos como nombres de usuario.
     * 
     * @param message Mensaje a enviar (ej: "K:NombreUsuario")
     */
    private void sendSerialMessage(String message) {
        try {
            serialListener.sendMessage(message);
            log.debug("Mensaje '{}' enviado al Arduino", message);
        } catch (Exception e) {
            log.error("Error enviando mensaje '{}' al Arduino: {}", message, e.getMessage());
        }
    }

    /**
     * Verifica si el sistema está en modo enrolamiento.
     * 
     * @return true si está en modo enrolamiento
     */
    public boolean isEnrollmentMode() {
        return currentMode.get() == SystemMode.ENROLAMIENTO;
    }

    /**
     * Verifica si el sistema está esperando validación del admin.
     * 
     * @return true si está esperando admin
     */
    public boolean isWaitingForAdmin() {
        return currentMode.get() == SystemMode.ESPERANDO_ADMIN;
    }

    /**
     * Obtiene el modo actual del sistema.
     * 
     * @return Modo actual (ACCESO, ESPERANDO_ADMIN o ENROLAMIENTO)
     */
    public SystemMode getCurrentMode() {
        return currentMode.get();
    }

    /**
     * Obtiene el estado actual del enrolamiento.
     * 
     * @return DTO con el estado actual
     */
    public EnrollmentStateDto getEnrollmentState() {
        SystemMode mode = currentMode.get();
        if (mode == SystemMode.ESPERANDO_ADMIN) {
            return EnrollmentStateDto.waitingAdmin(secondsRemaining.get());
        } else if (mode == SystemMode.ENROLAMIENTO) {
            return EnrollmentStateDto.active(secondsRemaining.get(), capturedUid.get());
        }
        return EnrollmentStateDto.inactive();
    }
}
