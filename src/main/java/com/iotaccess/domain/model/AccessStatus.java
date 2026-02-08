package com.iotaccess.domain.model;

/**
 * Enum que representa el estado de un intento de acceso.
 */
public enum AccessStatus {

    /** Acceso permitido - UID registrado en el sistema */
    GRANTED,

    /** Acceso denegado - UID no registrado */
    DENIED,

    /** Estado desconocido - Error de lectura o procesamiento */
    UNKNOWN
}
