package com.iotaccess.infrastructure.file;

import com.iotaccess.domain.exception.CsvProcessingException;
import com.iotaccess.domain.model.AccessRecord;
import com.iotaccess.domain.model.AccessStatus;
import com.iotaccess.domain.port.AccessLogWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Implementación del puerto AccessLogWriter que escribe a archivos CSV.
 * 
 * ENTREGABLE 3:
 * - Escribe simultáneamente al CSV normal y al CSV de respaldo.
 * - Guarda el nombre del archivo activo en un archivo binario oculto.
 * - Si el CSV normal es borrado, lo restaura desde el backup automáticamente.
 * - NO mantiene el archivo abierto (open-write-close en cada registro)
 * para permitir que el archivo pueda ser borrado durante la sesión.
 */
@Component
@Slf4j
public class CsvAccessLogWriter implements AccessLogWriter {

    private static final String CSV_HEADER = "timestamp,uid,status,station_id";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${csv.data-logs-path:./data_logs}")
    private String dataLogsPath;

    @Value("${csv.backup-data-logs-path:./data_logs_backup}")
    private String backupDataLogsPath;

    @Autowired
    private BinaryFileTracker binaryFileTracker;

    private String currentFilePath;
    private String currentBackupFilePath;
    private final Object writeLock = new Object();
    private int recordCount = 0;
    private boolean initialized = false;

    @Override
    public String initialize(String sessionName) {
        synchronized (writeLock) {
            try {
                // === CSV PRINCIPAL ===
                Path logsDir = Paths.get(dataLogsPath);
                if (!Files.exists(logsDir)) {
                    Files.createDirectories(logsDir);
                    log.info("Directorio creado: {}", logsDir.toAbsolutePath());
                }

                String fileName = String.format("%s_%s.csv",
                        sessionName,
                        LocalDate.now().format(FILE_DATE_FORMAT));

                Path filePath = logsDir.resolve(fileName);
                currentFilePath = filePath.toString();

                // Escribir header si es archivo nuevo
                if (!Files.exists(filePath)) {
                    writeLineToFile(filePath, CSV_HEADER, false);
                    log.info("Nuevo archivo CSV creado: {}", currentFilePath);
                } else {
                    log.info("Continuando archivo CSV existente: {}", currentFilePath);
                }

                // === CSV DE RESPALDO ===
                Path backupDir = Paths.get(backupDataLogsPath);
                if (!Files.exists(backupDir)) {
                    Files.createDirectories(backupDir);
                    log.info("Directorio de respaldo creado: {}", backupDir.toAbsolutePath());
                }

                String backupFileName = fileName.replace(".csv", "_backup.csv");
                Path backupFilePath = backupDir.resolve(backupFileName);
                currentBackupFilePath = backupFilePath.toString();

                if (!Files.exists(backupFilePath)) {
                    writeLineToFile(backupFilePath, CSV_HEADER, false);
                    log.info("Nuevo archivo CSV de respaldo creado: {}", currentBackupFilePath);
                } else {
                    log.info("Continuando archivo CSV de respaldo existente: {}", currentBackupFilePath);
                }

                // === ARCHIVO BINARIO ===
                binaryFileTracker.saveFileName(currentFilePath);

                recordCount = 0;
                initialized = true;
                return currentFilePath;

            } catch (IOException e) {
                throw CsvProcessingException.cannotWrite(currentFilePath, e);
            }
        }
    }

    @Override
    public void write(AccessRecord record) {
        synchronized (writeLock) {
            if (!initialized) {
                throw new IllegalStateException("Writer no inicializado. Llama a initialize() primero.");
            }

            try {
                String line = formatRecord(record);
                Path primaryPath = Paths.get(currentFilePath);
                Path backupPath = Paths.get(currentBackupFilePath);

                // === RESILIENCIA: Verificar que el CSV principal existe ===
                if (!Files.exists(primaryPath)) {
                    log.warn("⚠ CSV principal fue borrado durante ejecución: {}", currentFilePath);
                    recoverPrimaryFromBackup();
                }

                // === RESILIENCIA: Verificar que el archivo binario existe ===
                try {
                    Path binaryPath = Paths.get(binaryFileTracker.getTrackerFilePath());
                    if (!Files.exists(binaryPath)) {
                        log.warn("⚠ Archivo binario fue borrado durante ejecución, regenerando...");
                        binaryFileTracker.saveFileName(currentFilePath);
                        log.info("✓ Archivo binario regenerado: {}", binaryPath);
                    }
                } catch (Exception e) {
                    log.error("Error verificando archivo binario: {}", e.getMessage());
                }

                // Escribir al CSV principal (abrir-escribir-cerrar)
                writeLineToFile(primaryPath, line, true);

                // Escribir al CSV de respaldo (abrir-escribir-cerrar)
                try {
                    writeLineToFile(backupPath, line, true);
                } catch (IOException e) {
                    log.error("Error escribiendo al CSV de respaldo: {}", e.getMessage());
                }

                recordCount++;
                log.debug("Registro escrito en tiempo real (principal + respaldo): {}", line);

            } catch (IOException e) {
                throw CsvProcessingException.cannotWrite(currentFilePath, e);
            }
        }
    }

    /**
     * Escribe una línea a un archivo y cierra inmediatamente.
     * Esto permite que el archivo NO quede bloqueado por el proceso Java.
     *
     * @param filePath Ruta del archivo
     * @param line     Línea a escribir
     * @param append   true para agregar al final, false para sobreescribir
     */
    private void writeLineToFile(Path filePath, String line, boolean append) throws IOException {
        // Asegurar directorio padre
        if (filePath.getParent() != null && !Files.exists(filePath.getParent())) {
            Files.createDirectories(filePath.getParent());
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(filePath.toFile(), append),
                        StandardCharsets.UTF_8))) {
            writer.write(line);
            writer.newLine();
            writer.flush();
        }
        // El archivo se cierra aquí automáticamente (try-with-resources)
    }

    @Override
    public void flush() {
        // No necesario: cada escritura hace flush y cierre inmediato
        log.debug("Flush solicitado (no-op: escritura es inmediata)");
    }

    @Override
    public void close() {
        synchronized (writeLock) {
            // No hay writer persistente que cerrar
            log.info("Sesión CSV cerrada. Total registros escritos: {}", recordCount);
            currentFilePath = null;
            currentBackupFilePath = null;
            recordCount = 0;
            initialized = false;
        }
    }

    @Override
    public boolean isReady() {
        return initialized && currentFilePath != null;
    }

    /**
     * Obtiene la ruta del archivo actual.
     */
    public String getCurrentFilePath() {
        return currentFilePath;
    }

    /**
     * Obtiene la ruta del archivo de respaldo actual.
     */
    public String getCurrentBackupFilePath() {
        return currentBackupFilePath;
    }

    /**
     * Restaura el CSV principal desde el backup cuando fue borrado.
     */
    private void recoverPrimaryFromBackup() {
        log.info("Intentando restaurar CSV principal desde respaldo...");

        try {
            Path primaryPath = Paths.get(currentFilePath);
            Path backupPath = Paths.get(currentBackupFilePath);

            // Asegurar directorio padre
            if (primaryPath.getParent() != null && !Files.exists(primaryPath.getParent())) {
                Files.createDirectories(primaryPath.getParent());
            }

            // Copiar backup al lugar del principal
            if (Files.exists(backupPath)) {
                Files.copy(backupPath, primaryPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("✓ CSV principal restaurado desde respaldo: {} -> {}", backupPath, primaryPath);
            } else {
                // Si tampoco hay backup, crear archivo nuevo con header
                log.warn("No hay backup disponible. Creando CSV nuevo con header.");
                writeLineToFile(primaryPath, CSV_HEADER, false);
            }

            log.info("✓ CSV principal recuperado exitosamente");

        } catch (IOException e) {
            log.error("✗ Error fatal restaurando CSV principal: {}", e.getMessage());
        }
    }

    /**
     * Formatea un registro para escribir en CSV.
     */
    private String formatRecord(AccessRecord record) {
        return String.format("%s,%s,%s,%d",
                record.getTimestamp().format(TIMESTAMP_FORMAT),
                record.getUid(),
                record.getStatus().name(),
                record.getStationId() != null ? record.getStationId() : 1);
    }

    /**
     * Renombra el archivo CSV activo (y su backup) sin perder datos.
     * Mueve físicamente los archivos al nuevo nombre.
     */
    @Override
    public String renameFile(String newSessionName) {
        synchronized (writeLock) {
            if (!initialized || currentFilePath == null) {
                throw new IllegalStateException("Writer no inicializado.");
            }

            try {
                String date = LocalDate.now().format(FILE_DATE_FORMAT);
                String newFileName = newSessionName + "_" + date + ".csv";

                // === Renombrar CSV principal ===
                Path oldPrimary = Paths.get(currentFilePath);
                Path newPrimary = oldPrimary.getParent().resolve(newFileName);

                if (Files.exists(oldPrimary)) {
                    Files.move(oldPrimary, newPrimary, StandardCopyOption.REPLACE_EXISTING);
                    log.info("CSV principal renombrado: {} → {}", oldPrimary.getFileName(), newPrimary.getFileName());
                }
                currentFilePath = newPrimary.toString();

                // === Renombrar CSV de respaldo ===
                if (currentBackupFilePath != null) {
                    Path oldBackup = Paths.get(currentBackupFilePath);
                    String newBackupFileName = newFileName.replace(".csv", "_backup.csv");
                    Path newBackup = oldBackup.getParent().resolve(newBackupFileName);

                    if (Files.exists(oldBackup)) {
                        Files.move(oldBackup, newBackup, StandardCopyOption.REPLACE_EXISTING);
                        log.info("CSV backup renombrado: {} → {}", oldBackup.getFileName(), newBackup.getFileName());
                    }
                    currentBackupFilePath = newBackup.toString();
                }

                // === Actualizar archivo binario ===
                binaryFileTracker.saveFileName(currentFilePath);
                log.info("Tracker binario actualizado con nuevo nombre: {}", currentFilePath);

                return currentFilePath;

            } catch (IOException e) {
                throw CsvProcessingException.cannotWrite(currentFilePath, e);
            }
        }
    }

    /**
     * Cierra recursos al destruir el componente.
     */
    @jakarta.annotation.PreDestroy
    public void cleanup() {
        log.info("Limpiando recursos del CsvAccessLogWriter...");
        close();
    }
}
