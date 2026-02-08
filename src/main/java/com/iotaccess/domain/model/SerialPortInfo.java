package com.iotaccess.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Modelo de dominio que representa información de un puerto serial.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SerialPortInfo {

    /** Nombre del sistema del puerto (ej: COM3, /dev/ttyUSB0) */
    private String systemPortName;

    /** Descripción del puerto */
    private String descriptivePortName;

    /** Indica si el puerto está actualmente abierto */
    private boolean isOpen;

    /** Vendor ID del dispositivo USB (si aplica) */
    private String vendorId;

    /** Product ID del dispositivo USB (si aplica) */
    private String productId;
}
