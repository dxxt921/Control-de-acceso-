package com.iotaccess.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Modelo de dominio que representa un usuario registrado en el sistema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredUser {

    /**
     * UID único del dispositivo NFC (formato: XX XX XX XX)
     */
    private String uid;

    /**
     * Nombre del usuario asignado durante el enrolamiento
     */
    private String name;

    /**
     * Fecha y hora de registro del dispositivo
     */
    private LocalDateTime registeredAt;
}
