package com.iotaccess.domain.exception;

/**
 * Excepción lanzada cuando ocurre un error relacionado con el puerto serial.
 */
public class SerialPortException extends RuntimeException {

    public SerialPortException(String message) {
        super(message);
    }

    public SerialPortException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Excepción cuando el puerto no se puede abrir.
     */
    public static SerialPortException cannotOpen(String portName) {
        return new SerialPortException("No se puede abrir el puerto: " + portName);
    }

    /**
     * Excepción cuando el puerto no existe.
     */
    public static SerialPortException notFound(String portName) {
        return new SerialPortException("Puerto no encontrado: " + portName);
    }

    /**
     * Excepción cuando el puerto ya está en uso.
     */
    public static SerialPortException alreadyInUse(String portName) {
        return new SerialPortException("Puerto ya está en uso: " + portName);
    }
}
