package com.iotaccess.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Modelo de dominio que representa un registro de acceso.
 * Este es un objeto de dominio puro, independiente de la infraestructura.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessRecord {

    /** Identificador único del registro */
    private Long id;

    /** UID del tag NFC detectado (formato: "XX XX XX XX") */
    private String uid;

    /** Timestamp del momento de la lectura */
    private LocalDateTime timestamp;

    /** Estado del acceso (GRANTED, DENIED, UNKNOWN) */
    private AccessStatus status;

    /** ID de la estación donde se realizó la lectura */
    private Integer stationId;

    /** Nombre del usuario asociado al UID (si existe) */
    private String userName;

    /**
     * Crea un nuevo registro de acceso con timestamp actual.
     * 
     * @param uid       UID del tag NFC
     * @param status    Estado del acceso
     * @param stationId ID de la estación
     * @return Nuevo AccessRecord
     */
    public static AccessRecord create(String uid, AccessStatus status, Integer stationId) {
        return AccessRecord.builder()
                .uid(uid)
                .timestamp(LocalDateTime.now())
                .status(status)
                .stationId(stationId)
                .build();
    }

    /**
     * Verifica si el acceso fue permitido.
     * 
     * @return true si el acceso fue GRANTED
     */
    public boolean isGranted() {
        return AccessStatus.GRANTED.equals(this.status);
    }
}
