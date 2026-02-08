package com.iotaccess.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad JPA que mapea a la tabla access_logs.
 */
@Entity
@Table(name = "access_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "uid_detected", nullable = false, length = 50)
    private String uidDetected;

    @Column(name = "access_timestamp", nullable = false)
    private LocalDateTime accessTimestamp;

    @Column(name = "access_granted", nullable = false)
    private Boolean accessGranted;

    @Column(name = "station_id")
    private Integer stationId;
}
