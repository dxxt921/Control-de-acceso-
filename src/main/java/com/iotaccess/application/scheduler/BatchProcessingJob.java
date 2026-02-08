package com.iotaccess.application.scheduler;

import com.iotaccess.domain.model.AccessRecord;
import com.iotaccess.domain.model.RegisteredUser;
import com.iotaccess.domain.port.AccessLogReader;
import com.iotaccess.domain.port.AccessRepository;
import com.iotaccess.domain.port.UserRegistryPort;
import com.iotaccess.infrastructure.persistence.JpaUserRepository;
import com.iotaccess.infrastructure.persistence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Job programado para el procesamiento batch de archivos CSV.
 * Se ejecuta a las 10:00 PM para volcar los datos del día a MySQL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchProcessingJob {

    private final AccessLogReader logReader;
    private final AccessRepository accessRepository;
    private final UserRegistryPort userRegistryPort;
    private final JpaUserRepository jpaUserRepository;

    @Value("${operation.start-hour:8}")
    private int operationStartHour;

    @Value("${operation.end-hour:22}")
    private int operationEndHour;

    /**
     * Proceso batch que se ejecuta a las 10:00 PM todos los días.
     * Lee los archivos CSV del día, inserta en MySQL y los mueve a historial.
     * También sincroniza el registro de usuarios con MySQL.
     */
    @Scheduled(cron = "${batch.cron.expression:0 0 22 * * *}")
    public void processDailyBatch() {
        log.info("=== INICIANDO PROCESO BATCH ===");
        log.info("Hora de ejecución: {}", LocalDateTime.now());

        try {
            // 1. Sincronizar registro de usuarios a MySQL
            syncUserRegistry();

            // 2. Obtener archivos pendientes de logs de acceso
            List<Path> pendingFiles = logReader.getPendingFiles();

            if (pendingFiles.isEmpty()) {
                log.info("No hay archivos CSV pendientes para procesar");
                return;
            }

            log.info("Archivos a procesar: {}", pendingFiles.size());

            int totalProcessed = 0;
            int totalErrors = 0;

            // 3. Procesar cada archivo
            for (Path file : pendingFiles) {
                try {
                    int processed = processFile(file);
                    totalProcessed += processed;
                    log.info("Archivo procesado: {} ({} registros)", file.getFileName(), processed);

                } catch (Exception e) {
                    totalErrors++;
                    log.error("Error procesando archivo {}: {}", file.getFileName(), e.getMessage(), e);
                }
            }

            log.info("=== PROCESO BATCH COMPLETADO ===");
            log.info("Total registros procesados: {}", totalProcessed);
            log.info("Archivos con errores: {}", totalErrors);

        } catch (Exception e) {
            log.error("Error fatal en proceso batch: {}", e.getMessage(), e);
        }
    }

    /**
     * Sincroniza el registro de usuarios (user_registry.csv) con la base de datos
     * MySQL.
     * Los usuarios nuevos del CSV se insertan en la tabla users.
     */
    private void syncUserRegistry() {
        log.info("Sincronizando registro de usuarios con MySQL...");

        try {
            List<RegisteredUser> localUsers = userRegistryPort.findAll();
            int synced = 0;

            for (RegisteredUser user : localUsers) {
                // Verificar si ya existe en la base de datos
                if (!jpaUserRepository.existsByNfcUid(user.getUid())) {
                    // Crear entidad y guardar
                    UserEntity entity = UserEntity.builder()
                            .nfcUid(user.getUid())
                            .userName(user.getName())
                            .role("usuario")
                            .createdAt(user.getRegisteredAt())
                            .build();

                    jpaUserRepository.save(entity);
                    synced++;
                    log.debug("Usuario sincronizado a MySQL: {}", user.getUid());
                }
            }

            log.info("Sincronización de usuarios completada: {} nuevos usuarios", synced);

        } catch (Exception e) {
            log.error("Error sincronizando usuarios: {}", e.getMessage(), e);
        }
    }

    /**
     * Procesa un archivo CSV individual.
     * 
     * @param filePath Ruta del archivo
     * @return Número de registros procesados
     */
    private int processFile(Path filePath) {
        // 1. Leer registros del archivo
        List<AccessRecord> records = logReader.readFromFile(filePath);

        if (records.isEmpty()) {
            log.warn("Archivo vacío o sin registros válidos: {}", filePath);
            logReader.moveToHistory(filePath);
            return 0;
        }

        // 2. Insertar en base de datos
        int saved = accessRepository.saveAll(records);

        // 3. Mover archivo a historial
        logReader.moveToHistory(filePath);

        return saved;
    }

    /**
     * Verifica si el sistema está dentro del horario de operación.
     * 
     * @return true si está en horario de operación
     */
    public boolean isWithinOperationHours() {
        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.of(operationStartHour, 0);
        LocalTime end = LocalTime.of(operationEndHour, 0);

        return !now.isBefore(start) && now.isBefore(end);
    }

    /**
     * Ejecuta el proceso batch manualmente (para testing o triggers manuales).
     * 
     * @return Número de registros procesados
     */
    public int runManualBatch() {
        log.info("Ejecutando proceso batch manualmente...");

        List<Path> pendingFiles = logReader.getPendingFiles();
        int totalProcessed = 0;

        for (Path file : pendingFiles) {
            try {
                totalProcessed += processFile(file);
            } catch (Exception e) {
                log.error("Error en batch manual para {}: {}", file, e.getMessage());
            }
        }

        return totalProcessed;
    }
}
