package com.iotaccess.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Modelo de dominio que representa una sesión de captura activa.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptureSession {

    /** Nombre del puerto serial conectado */
    private String portName;

    /** Nombre de la sesión (para el archivo CSV) */
    private String sessionName;

    /** Timestamp de inicio de la sesión */
    private LocalDateTime startTime;

    /** Indica si la sesión está activa */
    private boolean active;

    /** Contador de registros capturados */
    private long recordCount;

    /** Ruta del archivo CSV actual */
    private String csvFilePath;

    /**
     * Incrementa el contador de registros.
     */
    public void incrementRecordCount() {
        this.recordCount++;
    }

    /**
     * Crea una nueva sesión de captura.
     * 
     * @param portName    Nombre del puerto
     * @param sessionName Nombre de la sesión
     * @return Nueva CaptureSession
     */
    public static CaptureSession start(String portName, String sessionName) {
        return CaptureSession.builder()
                .portName(portName)
                .sessionName(sessionName)
                .startTime(LocalDateTime.now())
                .active(true)
                .recordCount(0)
                .build();
    }
}
