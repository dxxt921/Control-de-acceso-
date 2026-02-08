package com.iotaccess.application.service;

import com.iotaccess.application.dto.EnrollmentStateDto;
import com.iotaccess.application.dto.RegisteredUserDto;
import com.iotaccess.domain.model.RegisteredUser;
import com.iotaccess.domain.model.SystemMode;
import com.iotaccess.infrastructure.serial.SerialListener;
import com.iotaccess.presentation.websocket.AccessWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio para gestión del modo de enrolamiento de dispositivos.
 * Maneja el estado global del sistema (ACCESO/ENROLAMIENTO) y la lógica de
 * captura de UIDs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceManagementService {

    private static final int ENROLLMENT_TIMEOUT_SECONDS = 20;

    private final IdentityService identityService;
    private final SerialListener serialListener;
    private final AccessWebSocketHandler webSocketHandler;

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

    /**
     * Inicia el modo de enrolamiento por 20 segundos.
     * 
     * @return Estado inicial del enrolamiento
     */
    public EnrollmentStateDto startEnrollmentMode() {
        log.info("Iniciando modo de enrolamiento...");

        // Limpiar estado anterior
        capturedUid.set(null);
        secondsRemaining.set(ENROLLMENT_TIMEOUT_SECONDS);

        // Cambiar modo
        currentMode.set(SystemMode.ENROLAMIENTO);

        // Enviar comando 'E' al Arduino
        sendSerialCommand('E');

        // Notificar via WebSocket
        webSocketHandler.broadcastEnrollmentMode(true, ENROLLMENT_TIMEOUT_SECONDS, null);

        // Iniciar countdown cada segundo
        countdownTask = scheduler.scheduleAtFixedRate(() -> {
            int remaining = secondsRemaining.decrementAndGet();

            if (remaining > 0) {
                webSocketHandler.broadcastEnrollmentMode(true, remaining, capturedUid.get());
            }
        }, 1, 1, TimeUnit.SECONDS);

        // Programar timeout
        timeoutTask = scheduler.schedule(() -> {
            log.info("Timeout de enrolamiento alcanzado");
            cancelEnrollment();
        }, ENROLLMENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("Modo enrolamiento activo. Esperando dispositivo NFC...");

        return EnrollmentStateDto.active(ENROLLMENT_TIMEOUT_SECONDS, null);
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

        // Enviar comando 'K' al Arduino (confirmación)
        sendSerialCommand('K');

        // Notificar éxito via WebSocket
        webSocketHandler.broadcastEnrollmentComplete(user);

        // Volver a modo acceso
        endEnrollmentMode();

        log.info("Enrolamiento completado: {} - {}", uid, name);

        return user;
    }

    /**
     * Cancela el modo de enrolamiento.
     */
    public void cancelEnrollment() {
        log.info("Cancelando modo de enrolamiento");
        endEnrollmentMode();
        webSocketHandler.broadcastEnrollmentMode(false, 0, null);
    }

    /**
     * Finaliza el modo de enrolamiento y vuelve a modo acceso.
     */
    private void endEnrollmentMode() {
        // Cancelar tareas programadas
        if (timeoutTask != null && !timeoutTask.isDone()) {
            timeoutTask.cancel(false);
        }
        if (countdownTask != null && !countdownTask.isDone()) {
            countdownTask.cancel(false);
        }

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
     * @param command Comando a enviar ('E', 'A', 'K')
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
     * Verifica si el sistema está en modo enrolamiento.
     * 
     * @return true si está en modo enrolamiento
     */
    public boolean isEnrollmentMode() {
        return currentMode.get() == SystemMode.ENROLAMIENTO;
    }

    /**
     * Obtiene el modo actual del sistema.
     * 
     * @return Modo actual (ACCESO o ENROLAMIENTO)
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
        if (currentMode.get() == SystemMode.ENROLAMIENTO) {
            return EnrollmentStateDto.active(secondsRemaining.get(), capturedUid.get());
        }
        return EnrollmentStateDto.inactive();
    }
}
