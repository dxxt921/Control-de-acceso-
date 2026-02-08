package com.iotaccess.application.dto;

import com.iotaccess.domain.model.CaptureSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;

/**
 * DTO para el estado de la sesión de captura.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStatusDto {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private boolean active;
    private String portName;
    private String sessionName;
    private String startTime;
    private long recordCount;
    private String csvFilePath;
    private String statusMessage;

    /**
     * Crea un DTO para sesión inactiva.
     */
    public static SessionStatusDto inactive() {
        return SessionStatusDto.builder()
                .active(false)
                .statusMessage("Sin sesión activa")
                .build();
    }

    /**
     * Crea un DTO desde un modelo de dominio.
     */
    public static SessionStatusDto fromDomain(CaptureSession session) {
        if (session == null || !session.isActive()) {
            return inactive();
        }

        return SessionStatusDto.builder()
                .active(true)
                .portName(session.getPortName())
                .sessionName(session.getSessionName())
                .startTime(session.getStartTime().format(TIME_FORMAT))
                .recordCount(session.getRecordCount())
                .csvFilePath(session.getCsvFilePath())
                .statusMessage("Capturando datos...")
                .build();
    }
}
