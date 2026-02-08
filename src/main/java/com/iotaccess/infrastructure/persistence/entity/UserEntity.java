package com.iotaccess.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad JPA que mapea a la tabla users.
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nfc_uid", unique = true, nullable = false, length = 50)
    private String nfcUid;

    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    @Column(name = "role", length = 50)
    private String role;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
