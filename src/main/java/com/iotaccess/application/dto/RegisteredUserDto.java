package com.iotaccess.application.dto;

import com.iotaccess.domain.model.RegisteredUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;

/**
 * DTO para transferencia de datos de usuario registrado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredUserDto {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String uid;
    private String name;
    private String registeredAt;

    /**
     * Convierte un modelo de dominio a DTO.
     */
    public static RegisteredUserDto fromDomain(RegisteredUser user) {
        return RegisteredUserDto.builder()
                .uid(user.getUid())
                .name(user.getName())
                .registeredAt(user.getRegisteredAt() != null
                        ? user.getRegisteredAt().format(DATE_FORMAT)
                        : "N/A")
                .build();
    }
}
