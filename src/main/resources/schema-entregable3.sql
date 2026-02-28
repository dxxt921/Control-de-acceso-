-- ===========================================
-- Entregable 3 - Objetos de Base de Datos
-- IoT Access System - smart_access_db
-- Separador: ;; (configurado en application.properties)
-- ===========================================

-- 1. Tabla de Auditoría
CREATE TABLE IF NOT EXISTS access_audit_log (
    audit_id INT AUTO_INCREMENT PRIMARY KEY,
    action_type VARCHAR(20) NOT NULL,
    uid_detected VARCHAR(50) NOT NULL,
    access_granted BOOLEAN NOT NULL,
    station_id INT,
    original_timestamp DATETIME NOT NULL,
    audit_timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    audit_description VARCHAR(255)
);;

-- 2. Tabla de Backup de access_logs
CREATE TABLE IF NOT EXISTS access_logs_backup (
    backup_id INT AUTO_INCREMENT PRIMARY KEY,
    original_id INT,
    uid_detected VARCHAR(50) NOT NULL,
    access_timestamp DATETIME NOT NULL,
    access_granted BOOLEAN NOT NULL,
    station_id INT,
    backup_timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    backup_batch VARCHAR(50)
);;

-- 3. Stored Procedure: Registrar Auditoría (llamado por el trigger)
DROP PROCEDURE IF EXISTS sp_registrar_auditoria;;

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
END;;

-- 4. Trigger: AFTER INSERT → LLAMA al stored procedure
DROP TRIGGER IF EXISTS trg_after_access_insert;;

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
END;;

-- 5. Stored Procedure: Backup Real de access_logs
DROP PROCEDURE IF EXISTS sp_backup_access_logs;;

CREATE PROCEDURE sp_backup_access_logs()
BEGIN
    DECLARE v_count INT DEFAULT 0;
    DECLARE v_batch_id VARCHAR(50);

    SET v_batch_id = CONCAT('BACKUP_', DATE_FORMAT(NOW(), '%Y%m%d_%H%i%s'));

    SELECT COUNT(*) INTO v_count
    FROM access_logs al
    LEFT JOIN access_logs_backup alb ON al.id = alb.original_id
    WHERE alb.original_id IS NULL;

    IF v_count > 0 THEN
        INSERT INTO access_logs_backup (original_id, uid_detected, access_timestamp, access_granted, station_id, backup_timestamp, backup_batch)
        SELECT al.id, al.uid_detected, al.access_timestamp, al.access_granted, al.station_id, NOW(), v_batch_id
        FROM access_logs al
        LEFT JOIN access_logs_backup alb ON al.id = alb.original_id
        WHERE alb.original_id IS NULL;
    END IF;

    SELECT v_batch_id AS lote_backup,
           v_count AS registros_nuevos_respaldados,
           NOW() AS fecha_ejecucion,
           'Backup completado exitosamente' AS mensaje;
END;;

-- 6. Stored Procedure: Estadísticas del día
DROP PROCEDURE IF EXISTS sp_daily_stats;;

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
END;;
