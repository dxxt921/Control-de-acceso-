package com.iotaccess.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad JPA que mapea a la tabla stations.
 */
@Entity
@Table(name = "stations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationEntity {

    @Id
    private Integer id;

    @Column(name = "location_name", nullable = false, length = 100)
    private String locationName;
}
