-- ===========================================
-- Entregable 3 - Objetos de Base de Datos
-- IoT Access System - smart_access_db
-- VERSIÓN PARA EJECUTAR MANUALMENTE EN MySQL Workbench
-- ===========================================

USE smart_access_db;

-- ===========================================
-- PASO 0: Habilitar el Event Scheduler de MySQL
-- Necesario para que los EVENTs programados funcionen
-- ===========================================
SET GLOBAL event_scheduler = ON;

-- ===========================================
-- 1. Tabla de Auditoría
-- Registra automáticamente cada acceso insertado
-- ===========================================
CREATE TABLE IF NOT EXISTS access_audit_log (
    audit_id INT AUTO_INCREMENT PRIMARY KEY,
    action_type VARCHAR(20) NOT NULL,
    uid_detected VARCHAR(50) NOT NULL,
    access_granted BOOLEAN NOT NULL,
    station_id INT,
    original_timestamp DATETIME NOT NULL,
    audit_timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    audit_description VARCHAR(255)
);

-- ===========================================
-- 2. Stored Procedure: Registrar Auditoría
--    Es LLAMADO por el TRIGGER.
--    Inserta un registro en la tabla de auditoría.
-- ===========================================
DROP PROCEDURE IF EXISTS sp_registrar_auditoria;

DELIMITER //
CREATE PROCEDURE sp_registrar_auditoria(
    IN p_uid VARCHAR(50),
    IN p_access_granted BOOLEAN,
    IN p_station_id INT,
    IN p_timestamp DATETIME
)
BEGIN
    INSERT INTO access_audit_log (
        action_type,
        uid_detected,
        access_granted,
        station_id,
        original_timestamp,
        audit_timestamp,
        audit_description
    ) VALUES (
        'INSERT',
        p_uid,
        p_access_granted,
        p_station_id,
        p_timestamp,
        NOW(),
        CONCAT('Acceso registrado - UID: ', p_uid,
               ' - Resultado: ', IF(p_access_granted, 'CONCEDIDO', 'DENEGADO'),
               ' - Estación: ', IFNULL(p_station_id, 1))
    );
END //
DELIMITER ;

-- ===========================================
-- 3. TRIGGER: AFTER INSERT en access_logs
--    Cada vez que se inserta un acceso, el trigger
--    LLAMA al stored procedure sp_registrar_auditoria.
-- ===========================================
DROP TRIGGER IF EXISTS trg_after_access_insert;

DELIMITER //
CREATE TRIGGER trg_after_access_insert
AFTER INSERT ON access_logs
FOR EACH ROW
BEGIN
    -- El trigger manda llamar al procedimiento almacenado
    CALL sp_registrar_auditoria(
        NEW.uid_detected,
        NEW.access_granted,
        NEW.station_id,
        NEW.access_timestamp
    );
END //
DELIMITER ;

-- ===========================================
-- 4. Stored Procedure: BACKUP REAL de access_logs
--    Exporta TODOS los registros de access_logs
--    a un archivo CSV REAL en disco.
--    Ruta: C:/ProgramData/MySQL/MySQL Server 9.1/Uploads/
--
--    Esto es un BACKUP REAL, no solo copiar a otra tabla.
--    Genera un archivo .csv con nombre único por fecha/hora.
-- ===========================================
DROP PROCEDURE IF EXISTS sp_backup_access_logs;

DELIMITER //
CREATE PROCEDURE sp_backup_access_logs()
BEGIN
    DECLARE v_count INT DEFAULT 0;
    DECLARE v_archivo VARCHAR(255);

    -- Contar registros a respaldar
    SELECT COUNT(*) INTO v_count FROM access_logs;

    IF v_count > 0 THEN
        -- Generar nombre de archivo único con fecha y hora
        SET v_archivo = CONCAT('C:/ProgramData/MySQL/MySQL Server 9.1/Uploads/backup_access_logs_',
                               DATE_FORMAT(NOW(), '%Y%m%d_%H%i%s'), '.csv');

        -- Exportar a archivo CSV real en disco
        SET @sql_backup = CONCAT(
            "SELECT 'ID', 'UID', 'FECHA_ACCESO', 'ACCESO_CONCEDIDO', 'ESTACION' ",
            "UNION ALL ",
            "SELECT CAST(id AS CHAR), uid_detected, CAST(access_timestamp AS CHAR), ",
            "CAST(access_granted AS CHAR), CAST(station_id AS CHAR) ",
            "FROM access_logs ",
            "INTO OUTFILE '", v_archivo, "' ",
            "FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\"' ",
            "LINES TERMINATED BY '\\n'"
        );

        PREPARE stmt FROM @sql_backup;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    -- Resultado del backup
    SELECT v_count AS registros_respaldados,
           v_archivo AS archivo_generado,
           NOW() AS fecha_ejecucion,
           'BACKUP REAL completado - Archivo CSV generado en disco' AS mensaje;
END //
DELIMITER ;

-- ===========================================
-- 5. Stored Procedure: Estadísticas del día
--    Retorna conteos de accesos del día actual
-- ===========================================
DROP PROCEDURE IF EXISTS sp_daily_stats;

DELIMITER //
CREATE PROCEDURE sp_daily_stats()
BEGIN
    SELECT
        COUNT(*) AS total_accesos,
        SUM(CASE WHEN access_granted = TRUE THEN 1 ELSE 0 END) AS accesos_concedidos,
        SUM(CASE WHEN access_granted = FALSE THEN 1 ELSE 0 END) AS accesos_denegados,
        COUNT(DISTINCT uid_detected) AS usuarios_unicos,
        MIN(access_timestamp) AS primer_acceso,
        MAX(access_timestamp) AS ultimo_acceso,
        CURDATE() AS fecha_consulta
    FROM access_logs
    WHERE DATE(access_timestamp) = CURDATE();
END //
DELIMITER ;

-- ===========================================
-- 6. EVENT (Disparador Programado):
--    Se ejecuta DIARIAMENTE a las 22:30 (10:30 PM)
--    Es decir, 30 minutos después del proceso batch (10:00 PM).
--
--    Este EVENT llama automáticamente al stored procedure
--    sp_backup_access_logs para generar el backup real en disco.
-- ===========================================
DROP EVENT IF EXISTS evt_backup_diario;

DELIMITER //
CREATE EVENT evt_backup_diario
ON SCHEDULE EVERY 1 DAY
STARTS CONCAT(CURDATE(), ' 22:30:00')
COMMENT 'Backup REAL automático - Genera archivo CSV en disco 30 min después del batch'
DO
BEGIN
    CALL sp_backup_access_logs();
END //
DELIMITER ;

-- ===========================================
-- VERIFICACIÓN
-- ===========================================

-- Ver tablas creadas
SELECT TABLE_NAME, TABLE_ROWS, CREATE_TIME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'smart_access_db'
ORDER BY CREATE_TIME DESC;

-- Ver el trigger
SHOW TRIGGERS FROM smart_access_db;

-- Ver stored procedures
SHOW PROCEDURE STATUS WHERE Db = 'smart_access_db';

-- Ver el EVENT programado
SHOW EVENTS FROM smart_access_db;

-- Verificar que el Event Scheduler está activo
SHOW VARIABLES LIKE 'event_scheduler';

-- ===========================================
-- PRUEBAS MANUALES:
-- ===========================================
-- Probar estadísticas:
-- CALL sp_daily_stats();
--
-- Probar backup real (genera archivo en C:/ProgramData/MySQL/MySQL Server 9.1/Uploads/):
-- CALL sp_backup_access_logs();
--
-- Verificar archivo generado en Windows:
-- Ir a C:\ProgramData\MySQL\MySQL Server 9.1\Uploads\ y buscar backup_access_logs_*.csv
