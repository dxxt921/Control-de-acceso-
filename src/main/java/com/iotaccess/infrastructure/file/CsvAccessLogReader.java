package com.iotaccess.infrastructure.file;

import com.iotaccess.domain.exception.CsvProcessingException;
import com.iotaccess.domain.model.AccessRecord;
import com.iotaccess.domain.model.AccessStatus;
import com.iotaccess.domain.port.AccessLogReader;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementación del puerto AccessLogReader para leer archivos CSV.
 * Usado por el batch job para procesar los archivos del día.
 */
@Component
@Slf4j
public class CsvAccessLogReader implements AccessLogReader {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${csv.data-logs-path:./data_logs}")
    private String dataLogsPath;

    @Value("${csv.history-path:./history}")
    private String historyPath;

    private final CsvAccessLogWriter csvAccessLogWriter;

    public CsvAccessLogReader(CsvAccessLogWriter csvAccessLogWriter) {
        this.csvAccessLogWriter = csvAccessLogWriter;
    }

    @Override
    public List<AccessRecord> readFromFile(Path filePath) {
        log.info("Leyendo archivo CSV: {}", filePath);
        List<AccessRecord> records = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(filePath.toFile()))) {
            List<String[]> lines = reader.readAll();

            // Saltar header
            for (int i = 1; i < lines.size(); i++) {
                try {
                    AccessRecord record = parseLine(lines.get(i), i + 1);
                    if (record != null) {
                        records.add(record);
                    }
                } catch (Exception e) {
                    log.warn("Error parseando línea {} del archivo {}: {}",
                            i + 1, filePath, e.getMessage());
                }
            }

            log.info("Leídos {} registros del archivo {}", records.size(), filePath);

        } catch (IOException | CsvException e) {
            throw CsvProcessingException.cannotRead(filePath.toString(), e);
        }

        return records;
    }

    @Override
    public List<Path> getPendingFiles() {
        Path logsDir = Paths.get(dataLogsPath);

        if (!Files.exists(logsDir)) {
            log.warn("Directorio de logs no existe: {}", logsDir);
            return new ArrayList<>();
        }

        // Obtener la ruta del archivo activo (sesión en curso)
        String activeFilePath = csvAccessLogWriter.getCurrentFilePath();

        try (Stream<Path> files = Files.list(logsDir)) {
            List<Path> csvFiles = files
                    .filter(path -> path.toString().endsWith(".csv"))
                    .filter(Files::isRegularFile)
                    // Excluir archivos de registro de usuarios
                    .filter(path -> !path.getFileName().toString().startsWith("user_registry"))
                    // Excluir el archivo de la sesión activa
                    .filter(path -> {
                        if (activeFilePath != null) {
                            try {
                                return !path.toAbsolutePath().normalize()
                                        .equals(Paths.get(activeFilePath).toAbsolutePath().normalize());
                            } catch (Exception e) {
                                return true;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            log.info("Encontrados {} archivos CSV pendientes (excluido archivo activo: {})",
                    csvFiles.size(), activeFilePath != null ? Paths.get(activeFilePath).getFileName() : "ninguno");
            return csvFiles;

        } catch (IOException e) {
            log.error("Error listando archivos CSV: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public Path moveToHistory(Path filePath) {
        try {
            // Asegurar que el directorio history existe
            Path historyDir = Paths.get(historyPath);
            if (!Files.exists(historyDir)) {
                Files.createDirectories(historyDir);
                log.info("Directorio de historial creado: {}", historyDir);
            }

            // Mover archivo
            Path targetPath = historyDir.resolve(filePath.getFileName());

            // Si ya existe, agregar sufijo
            if (Files.exists(targetPath)) {
                String fileName = filePath.getFileName().toString();
                String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                String extension = fileName.substring(fileName.lastIndexOf('.'));
                int counter = 1;

                do {
                    targetPath = historyDir.resolve(
                            String.format("%s_%d%s", baseName, counter++, extension));
                } while (Files.exists(targetPath));
            }

            Files.move(filePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Archivo movido a historial: {} -> {}", filePath, targetPath);

            return targetPath;

        } catch (IOException e) {
            log.error("Error moviendo archivo a historial: {}", e.getMessage());
            throw new CsvProcessingException("Error moviendo archivo a historial", e);
        }
    }

    /**
     * Parsea una línea CSV a un AccessRecord.
     * Formato esperado: timestamp,uid,status,station_id
     */
    private AccessRecord parseLine(String[] fields, int lineNumber) {
        if (fields.length < 3) {
            log.warn("Línea {} con formato incompleto: {} campos", lineNumber, fields.length);
            return null;
        }

        try {
            LocalDateTime timestamp = LocalDateTime.parse(fields[0].trim(), TIMESTAMP_FORMAT);
            String uid = fields[1].trim();
            AccessStatus status = parseStatus(fields[2].trim());
            Integer stationId = fields.length > 3 ? parseInteger(fields[3].trim()) : 1;

            return AccessRecord.builder()
                    .uid(uid)
                    .timestamp(timestamp)
                    .status(status)
                    .stationId(stationId)
                    .build();

        } catch (DateTimeParseException e) {
            log.warn("Error parseando timestamp en línea {}: {}", lineNumber, fields[0]);
            return null;
        }
    }

    /**
     * Parsea el status del string.
     */
    private AccessStatus parseStatus(String status) {
        try {
            return AccessStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AccessStatus.UNKNOWN;
        }
    }

    /**
     * Parsea un integer de forma segura.
     */
    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
