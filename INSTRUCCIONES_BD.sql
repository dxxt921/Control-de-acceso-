-- =====================================================
-- INSTRUCCIONES PARA BASE DE DATOS - ENTREGABLE 3
-- Ejecutar TODO este archivo en MySQL Workbench
-- =====================================================

USE smart_access_db;

-- =====================================================
-- PASO 1: Borrar todo lo viejo
-- =====================================================
DROP TRIGGER IF EXISTS trg_after_access_insert;
DROP EVENT IF EXISTS evt_backup_diario;
DROP PROCEDURE IF EXISTS sp_backup_access_logs;
DROP PROCEDURE IF EXISTS sp_registrar_auditoria;
DROP PROCEDURE IF EXISTS sp_daily_stats;
DROP TABLE IF EXISTS access_logs_backup;
DROP TABLE IF EXISTS access_audit_log;

-- =====================================================
-- PASO 2: Habilitar el Event Scheduler
-- =====================================================
SET GLOBAL event_scheduler = ON;

-- =====================================================
-- PASO 3: Crear tabla de auditoría
-- =====================================================
CREATE TABLE access_audit_log (
    audit_id INT AUTO_INCREMENT PRIMARY KEY,
    action_type VARCHAR(20) NOT NULL,
    uid_detected VARCHAR(50) NOT NULL,
    access_granted BOOLEAN NOT NULL,
    station_id INT,
    original_timestamp DATETIME NOT NULL,
    audit_timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    audit_description VARCHAR(255)
);

-- =====================================================
-- PASO 4: Stored Procedure que registra auditoría
-- (Este es el que llama el trigger)
-- =====================================================
DELIMITER //
CREATE PROCEDURE sp_registrar_auditoria(
    IN p_uid VARCHAR(50),
    IN p_access_granted BOOLEAN,
    IN p_station_id INT,
    IN p_timestamp DATETIME
)
BEGIN
    INSERT INTO access_audit_log (
        action_type, uid_detected, access_granted, station_id,
        original_timestamp, audit_timestamp, audit_description
    ) VALUES (
        'INSERT', p_uid, p_access_granted, p_station_id, p_timestamp, NOW(),
        CONCAT('Acceso registrado - UID: ', p_uid,
               ' - Resultado: ', IF(p_access_granted, 'CONCEDIDO', 'DENEGADO'))
    );
END //
DELIMITER ;

-- =====================================================
-- PASO 5: Trigger que LLAMA al stored procedure
-- =====================================================
DELIMITER //
CREATE TRIGGER trg_after_access_insert
AFTER INSERT ON access_logs
FOR EACH ROW
BEGIN
    CALL sp_registrar_auditoria(
        NEW.uid_detected,
        NEW.access_granted,
        NEW.station_id,
        NEW.access_timestamp
    );
END //
DELIMITER ;

-- =====================================================
-- PASO 6: Stored Procedure de BACKUP REAL
-- Exporta los datos a un archivo CSV en disco
-- =====================================================
DELIMITER //
CREATE PROCEDURE sp_backup_access_logs()
BEGIN
    DECLARE v_count INT DEFAULT 0;
    DECLARE v_archivo VARCHAR(255);

    SELECT COUNT(*) INTO v_count FROM access_logs;

    IF v_count > 0 THEN
        SET v_archivo = CONCAT('C:/ProgramData/MySQL/MySQL Server 9.1/Uploads/backup_access_logs_',
                               DATE_FORMAT(NOW(), '%Y%m%d_%H%i%s'), '.csv');

        SET @sql_backup = CONCAT(
            "SELECT 'ID','UID','FECHA_ACCESO','ACCESO_CONCEDIDO','ESTACION' ",
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

    SELECT v_count AS registros_respaldados,
           v_archivo AS archivo_generado,
           NOW() AS fecha_ejecucion,
           'BACKUP REAL - Archivo CSV generado en disco' AS mensaje;
END //
DELIMITER ;

-- =====================================================
-- PASO 7: Stored Procedure de estadísticas
-- =====================================================
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

-- =====================================================
-- PASO 8: EVENT - Disparador programado a las 22:30
-- Se ejecuta 30 min después del batch (22:00)
-- =====================================================
DELIMITER //
CREATE EVENT evt_backup_diario
ON SCHEDULE EVERY 1 DAY
STARTS CONCAT(CURDATE(), ' 22:30:00')
COMMENT 'Backup REAL diario - 30 min después del batch'
DO
BEGIN
    CALL sp_backup_access_logs();
END //
DELIMITER ;

-- =====================================================
-- PASO 9: VERIFICAR que todo se creó bien
-- =====================================================
SHOW TRIGGERS FROM smart_access_db;
SHOW PROCEDURE STATUS WHERE Db = 'smart_access_db';
SHOW EVENTS FROM smart_access_db;

-- =====================================================
-- PASO 10: PROBAR
-- Ejecutar el backup manualmente:
-- CALL sp_backup_access_logs();
-- Luego ir a: C:\ProgramData\MySQL\MySQL Server 9.1\Uploads\
-- Ahí estará el archivo backup_access_logs_XXXXXXXX.csv
-- =====================================================
