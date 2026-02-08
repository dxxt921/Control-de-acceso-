package com.iotaccess.application.dto;

import com.iotaccess.domain.model.AccessRecord;
import com.iotaccess.domain.model.AccessStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DTO para transferir información de acceso a la capa de presentación.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessRecordDto {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Long id;
    private String uid;
    private String timestamp;
    private String date;
    private String time;
    private String status;
    private String statusClass;
    private String userName;
    private Integer stationId;

    /**
     * Crea un DTO desde un modelo de dominio.
     */
    public static AccessRecordDto fromDomain(AccessRecord record) {
        String statusClass = switch (record.getStatus()) {
            case GRANTED -> "success";
            case DENIED -> "danger";
            case UNKNOWN -> "warning";
        };

        String statusText = switch (record.getStatus()) {
            case GRANTED -> "Permitido";
            case DENIED -> "Denegado";
            case UNKNOWN -> "Desconocido";
        };

        return AccessRecordDto.builder()
                .id(record.getId())
                .uid(record.getUid())
                .timestamp(record.getTimestamp().toString())
                .date(record.getTimestamp().format(DATE_FORMAT))
                .time(record.getTimestamp().format(TIME_FORMAT))
                .status(statusText)
                .statusClass(statusClass)
                .userName(record.getUserName() != null ? record.getUserName() : "Desconocido")
                .stationId(record.getStationId())
                .build();
    }
}
