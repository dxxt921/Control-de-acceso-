package com.iotaccess.application.services;

import com.iotaccess.application.dtos.EnrollmentStateDto;
import com.iotaccess.application.dtos.RegisteredUserDto;
import com.iotaccess.domain.model.SystemMode;

/**
 * Contrato para el servicio de gestión de dispositivos y enrolamiento.
 * Define las operaciones para el flujo de registro de nuevos dispositivos NFC.
 */
public interface DeviceManagementService {

    /**
     * Inicia el proceso de enrolamiento solicitando la validación del admin.
     *
     * @return Estado inicial del enrolamiento (esperando admin)
     */
    EnrollmentStateDto startEnrollmentMode();

    /**
     * Valida si el UID recibido corresponde al administrador.
     *
     * @param uid UID de la tarjeta escaneada
     * @return true si el admin fue validado correctamente
     */
    boolean validateAdminUid(String uid);

    /**
     * Captura un UID durante el modo de enrolamiento.
     *
     * @param uid UID capturado del dispositivo NFC
     */
    void captureUidForEnrollment(String uid);

    /**
     * Confirma el enrolamiento de un dispositivo con su nombre.
     *
     * @param uid  UID del dispositivo
     * @param name Nombre asignado por el administrador
     * @return DTO del usuario registrado
     */
    RegisteredUserDto confirmEnrollment(String uid, String name);

    /**
     * Cancela el modo de enrolamiento o validación de admin.
     */
    void cancelEnrollment();

    /**
     * Verifica si el sistema está en modo enrolamiento.
     *
     * @return true si está en modo enrolamiento
     */
    boolean isEnrollmentMode();

    /**
     * Verifica si el sistema está esperando validación del admin.
     *
     * @return true si está esperando admin
     */
    boolean isWaitingForAdmin();

    /**
     * Obtiene el modo actual del sistema.
     *
     * @return Modo actual (ACCESO, ESPERANDO_ADMIN o ENROLAMIENTO)
     */
    SystemMode getCurrentMode();

    /**
     * Obtiene el estado actual del enrolamiento.
     *
     * @return DTO con el estado actual
     */
    EnrollmentStateDto getEnrollmentState();
}
