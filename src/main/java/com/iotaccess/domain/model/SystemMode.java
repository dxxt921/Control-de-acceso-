package com.iotaccess.domain.model;

/**
 * Enum que define los modos de operación del sistema.
 */
public enum SystemMode {
    /**
     * Modo normal de acceso - valida UIDs y activa el servomotor
     */
    ACCESO,

    /**
     * Modo de enrolamiento - captura UIDs para registrar nuevos dispositivos
     */
    ENROLAMIENTO
}
