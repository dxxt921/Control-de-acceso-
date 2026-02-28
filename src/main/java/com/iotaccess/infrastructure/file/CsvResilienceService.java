package com.iotaccess.infrastructure.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Servicio de resiliencia para archivos CSV.
 * Verifica periódicamente la existencia de los CSV activos y los
 * restaura desde sus respaldos si fueron borrados mientras el programa
 * está activo, evitando que el sistema se crashee.
 */
@Service
@Slf4j
public class CsvResilienceService {

    @Value("${csv.data-logs-path:./data_logs}")
    private String dataLogsPath;

    @Value("${csv.backup-data-logs-path:./data_logs_backup}")
    private String backupDataLogsPath;

    @Value("${csv.user-registry-path:./data_logs/user_registry.csv}")
    private String userRegistryPath;

    @Value("${csv.backup-user-registry-path:./data_logs_backup/user_registry_backup.csv}")
    private String backupUserRegistryPath;

    private final CsvAccessLogWriter csvAccessLogWriter;

    public CsvResilienceService(CsvAccessLogWriter csvAccessLogWriter) {
        this.csvAccessLogWriter = csvAccessLogWriter;
    }

    /**
     * Verificación periódica cada 30 segundos.
     * Comprueba que los archivos CSV activos existan. Si fueron borrados,
     * los restaura del backup.
     */
    @Scheduled(fixedDelay = 30000)
    public void periodicCheck() {
        // Verificar CSV de accesos activo
        String activeCsvPath = csvAccessLogWriter.getCurrentFilePath();
        if (activeCsvPath != null) {
            String backupPath = getBackupPathFor(activeCsvPath);
            if (backupPath != null) {
                verifyAndRecover(activeCsvPath, backupPath);
            }
        }

        // Verificar CSV de registro de usuarios
        verifyAndRecover(userRegistryPath, backupUserRegistryPath);
    }

    /**
     * Verifica si el archivo primario existe. Si no existe pero el backup sí,
     * restaura el primario desde el backup.
     *
     * @param primaryPath Ruta del archivo principal
     * @param backupPath  Ruta del archivo de respaldo
     * @return true si el archivo primario existe (o fue restaurado exitosamente)
     */
    public boolean verifyAndRecover(String primaryPath, String backupPath) {
        if (primaryPath == null)
            return false;

        Path primary = Paths.get(primaryPath);
        Path backup = Paths.get(backupPath);

        // Si el primario existe, todo bien
        if (Files.exists(primary)) {
            return true;
        }

        // El primario no existe - intentar restaurar del backup
        log.warn("⚠ Archivo CSV principal NO encontrado: {}", primaryPath);

        if (Files.exists(backup)) {
            try {
                // Asegurar que el directorio padre existe
                if (primary.getParent() != null && !Files.exists(primary.getParent())) {
                    Files.createDirectories(primary.getParent());
                }

                // Copiar backup al lugar del primario
                Files.copy(backup, primary, StandardCopyOption.REPLACE_EXISTING);
                log.info("✓ Archivo CSV restaurado exitosamente desde backup: {} -> {}",
                        backup, primary);
                return true;

            } catch (IOException e) {
                log.error("✗ Error restaurando CSV desde backup: {}", e.getMessage());
                return false;
            }
        } else {
            log.warn("✗ Ni el archivo principal ni el backup existen: {} / {}", primaryPath, backupPath);
            return false;
        }
    }

    /**
     * Dada la ruta de un archivo CSV de acceso, obtiene la ruta de su backup
     * correspondiente.
     */
    public String getBackupPathFor(String primaryPath) {
        if (primaryPath == null)
            return null;

        try {
            Path primary = Paths.get(primaryPath);
            String fileName = primary.getFileName().toString();

            // Generar nombre de backup: nombre_backup.csv
            String backupFileName;
            if (fileName.endsWith(".csv")) {
                backupFileName = fileName.substring(0, fileName.length() - 4) + "_backup.csv";
            } else {
                backupFileName = fileName + "_backup";
            }

            Path backupDir = Paths.get(backupDataLogsPath);
            return backupDir.resolve(backupFileName).toString();
        } catch (Exception e) {
            log.error("Error generando ruta de backup para {}: {}", primaryPath, e.getMessage());
            return null;
        }
    }

    /**
     * Verifica si un archivo CSV existe.
     *
     * @param filePath Ruta del archivo a verificar
     * @return true si el archivo existe
     */
    public boolean csvFileExists(String filePath) {
        if (filePath == null)
            return false;
        return Files.exists(Paths.get(filePath));
    }
}
