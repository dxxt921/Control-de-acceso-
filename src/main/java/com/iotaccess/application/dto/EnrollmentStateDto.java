package com.iotaccess.application.dto;

import com.iotaccess.domain.model.SystemMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para el estado del modo de enrolamiento.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentStateDto {

    /**
     * Indica si el modo enrolamiento está activo
     */
    private boolean active;

    /**
     * Segundos restantes antes de timeout
     */
    private int secondsRemaining;

    /**
     * UID capturado pendiente de confirmación (null si no hay)
     */
    private String capturedUid;

    /**
     * Modo actual del sistema
     */
    private String mode;

    /**
     * Crea un estado inactivo.
     */
    public static EnrollmentStateDto inactive() {
        return EnrollmentStateDto.builder()
                .active(false)
                .secondsRemaining(0)
                .capturedUid(null)
                .mode(SystemMode.ACCESO.name())
                .build();
    }

    /**
     * Crea un estado activo.
     */
    public static EnrollmentStateDto active(int secondsRemaining, String capturedUid) {
        return EnrollmentStateDto.builder()
                .active(true)
                .secondsRemaining(secondsRemaining)
                .capturedUid(capturedUid)
                .mode(SystemMode.ENROLAMIENTO.name())
                .build();
    }

    /**
     * Crea un estado de espera de validación del admin.
     */
    public static EnrollmentStateDto waitingAdmin(int secondsRemaining) {
        return EnrollmentStateDto.builder()
                .active(true)
                .secondsRemaining(secondsRemaining)
                .capturedUid(null)
                .mode(SystemMode.ESPERANDO_ADMIN.name())
                .build();
    }
}
