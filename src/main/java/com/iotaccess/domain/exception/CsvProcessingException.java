package com.iotaccess.domain.exception;

/**
 * Excepción lanzada cuando ocurre un error en el procesamiento de archivos CSV.
 */
public class CsvProcessingException extends RuntimeException {

    public CsvProcessingException(String message) {
        super(message);
    }

    public CsvProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Excepción cuando no se puede escribir al archivo.
     */
    public static CsvProcessingException cannotWrite(String filePath, Throwable cause) {
        return new CsvProcessingException("No se puede escribir al archivo: " + filePath, cause);
    }

    /**
     * Excepción cuando no se puede leer el archivo.
     */
    public static CsvProcessingException cannotRead(String filePath, Throwable cause) {
        return new CsvProcessingException("No se puede leer el archivo: " + filePath, cause);
    }

    /**
     * Excepción cuando el formato del archivo es inválido.
     */
    public static CsvProcessingException invalidFormat(String filePath, int lineNumber) {
        return new CsvProcessingException(
                String.format("Formato inválido en archivo %s, línea %d", filePath, lineNumber));
    }
}
