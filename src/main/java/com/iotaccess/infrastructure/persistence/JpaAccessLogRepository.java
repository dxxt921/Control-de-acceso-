package com.iotaccess.infrastructure.persistence;

import com.iotaccess.infrastructure.persistence.entity.AccessLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio JPA para operaciones con access_logs.
 */
@Repository
public interface JpaAccessLogRepository extends JpaRepository<AccessLogEntity, Integer> {

    /**
     * Busca logs de acceso entre dos fechas.
     */
    @Query("SELECT a FROM AccessLogEntity a WHERE a.accessTimestamp BETWEEN :start AND :end ORDER BY a.accessTimestamp DESC")
    List<AccessLogEntity> findByTimestampBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Cuenta los accesos de hoy.
     */
    @Query("SELECT COUNT(a) FROM AccessLogEntity a WHERE a.accessTimestamp >= :startOfDay")
    long countTodayAccesses(@Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Busca los últimos N registros.
     */
    @Query("SELECT a FROM AccessLogEntity a ORDER BY a.accessTimestamp DESC LIMIT :limit")
    List<AccessLogEntity> findLatest(@Param("limit") int limit);
}
