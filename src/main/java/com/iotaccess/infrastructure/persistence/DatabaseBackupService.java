package com.iotaccess.infrastructure.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Servicio para ejecutar los stored procedures de la base de datos.
 * Permite hacer backup de access_logs y consultar estadísticas diarias.
 */
@Service
@Slf4j
public class DatabaseBackupService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Ejecuta el stored procedure sp_backup_access_logs.
     * Copia los registros de access_logs que aún no están en access_logs_backup.
     *
     * @return Cantidad de registros respaldados
     */
    public int executeBackup() {
        log.info("Ejecutando stored procedure sp_backup_access_logs...");
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap("CALL sp_backup_access_logs()");
            int count = ((Number) result.get("registros_respaldados")).intValue();
            log.info("Backup completado: {} registros respaldados en access_logs_backup", count);
            return count;
        } catch (Exception e) {
            log.error("Error ejecutando sp_backup_access_logs: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Ejecuta el stored procedure sp_daily_stats.
     * Retorna estadísticas de acceso del día actual.
     *
     * @return Map con las estadísticas del día
     */
    public Map<String, Object> getDailyStats() {
        log.info("Ejecutando stored procedure sp_daily_stats...");
        try {
            Map<String, Object> stats = jdbcTemplate.queryForMap("CALL sp_daily_stats()");
            log.info("Estadísticas del día: total={}, concedidos={}, denegados={}, únicos={}",
                    stats.get("total_accesos"),
                    stats.get("accesos_concedidos"),
                    stats.get("accesos_denegados"),
                    stats.get("usuarios_unicos"));
            return stats;
        } catch (Exception e) {
            log.error("Error ejecutando sp_daily_stats: {}", e.getMessage());
            return Map.of(
                    "total_accesos", 0,
                    "accesos_concedidos", 0,
                    "accesos_denegados", 0,
                    "usuarios_unicos", 0,
                    "error", e.getMessage());
        }
    }
}
