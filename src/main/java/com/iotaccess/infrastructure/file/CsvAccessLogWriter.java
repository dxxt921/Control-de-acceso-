package com.iotaccess.infrastructure.file;

import com.iotaccess.domain.exception.CsvProcessingException;
import com.iotaccess.domain.model.AccessRecord;
import com.iotaccess.domain.model.AccessStatus;
import com.iotaccess.domain.port.AccessLogWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Implementación del puerto AccessLogWriter que escribe a archivos CSV.
 * Usa BufferedWriter para escritura segura y eficiente.
 */
@Component
@Slf4j
public class CsvAccessLogWriter implements AccessLogWriter {

    private static final String CSV_HEADER = "timestamp,uid,status,station_id";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${csv.data-logs-path:./data_logs}")
    private String dataLogsPath;

    private BufferedWriter writer;
    private String currentFilePath;
    private final Object writeLock = new Object();
    private int recordCount = 0;
    private static final int FLUSH_INTERVAL = 5; // Flush cada 5 registros

    @Override
    public String initialize(String sessionName) {
        synchronized (writeLock) {
            try {
                // Cerrar writer anterior si existe
                if (writer != null) {
                    close();
                }

                // Asegurar que el directorio existe
                Path logsDir = Paths.get(dataLogsPath);
                if (!Files.exists(logsDir)) {
                    Files.createDirectories(logsDir);
                    log.info("Directorio creado: {}", logsDir.toAbsolutePath());
                }

                // Crear nombre de archivo con fecha
                String fileName = String.format("%s_%s.csv",
                        sessionName,
                        LocalDate.now().format(FILE_DATE_FORMAT));

                Path filePath = logsDir.resolve(fileName);
                currentFilePath = filePath.toString();

                // Verificar si el archivo ya existe (para continuar sesión)
                boolean isNewFile = !Files.exists(filePath);

                // Crear writer con append
                writer = new BufferedWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(filePath.toFile(), true),
                                StandardCharsets.UTF_8),
                        8192 // Buffer de 8KB
                );

                // Escribir header si es archivo nuevo
                if (isNewFile) {
                    writer.write(CSV_HEADER);
                    writer.newLine();
                    writer.flush();
                    log.info("Nuevo archivo CSV creado: {}", currentFilePath);
                } else {
                    log.info("Continuando archivo CSV existente: {}", currentFilePath);
                }

                recordCount = 0;
                return currentFilePath;

            } catch (IOException e) {
                throw CsvProcessingException.cannotWrite(currentFilePath, e);
            }
        }
    }

    @Override
    public void write(AccessRecord record) {
        synchronized (writeLock) {
            if (writer == null) {
                throw new IllegalStateException("Writer no inicializado. Llama a initialize() primero.");
            }

            try {
                String line = formatRecord(record);
                writer.write(line);
                writer.newLine();
                recordCount++;

                log.debug("Registro escrito: {}", line);

                // Flush periódico para evitar pérdida de datos
                if (recordCount % FLUSH_INTERVAL == 0) {
                    writer.flush();
                    log.debug("Buffer flush realizado ({} registros)", recordCount);
                }

            } catch (IOException e) {
                throw CsvProcessingException.cannotWrite(currentFilePath, e);
            }
        }
    }

    @Override
    public void flush() {
        synchronized (writeLock) {
            if (writer != null) {
                try {
                    writer.flush();
                    log.debug("Flush manual realizado");
                } catch (IOException e) {
                    log.error("Error en flush: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public void close() {
        synchronized (writeLock) {
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                    log.info("Writer cerrado. Total registros escritos: {}", recordCount);
                } catch (IOException e) {
                    log.error("Error cerrando writer: {}", e.getMessage());
                } finally {
                    writer = null;
                    currentFilePath = null;
                    recordCount = 0;
                }
            }
        }
    }

    @Override
    public boolean isReady() {
        return writer != null;
    }

    /**
     * Obtiene la ruta del archivo actual.
     */
    public String getCurrentFilePath() {
        return currentFilePath;
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
     * Cierra recursos al destruir el componente.
     */
    @jakarta.annotation.PreDestroy
    public void cleanup() {
        log.info("Limpiando recursos del CsvAccessLogWriter...");
        close();
    }
}
