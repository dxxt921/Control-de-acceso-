package com.iotaccess.application.scheduler;

import com.iotaccess.domain.model.AccessRecord;
import com.iotaccess.domain.model.RegisteredUser;
import com.iotaccess.domain.port.AccessLogReader;
import com.iotaccess.domain.port.AccessRepository;
import com.iotaccess.domain.port.UserRegistryPort;
import com.iotaccess.infrastructure.file.CsvAccessLogWriter;
import com.iotaccess.infrastructure.persistence.DatabaseBackupService;
import com.iotaccess.infrastructure.persistence.JpaUserRepository;
import com.iotaccess.infrastructure.persistence.entity.UserEntity;
import com.iotaccess.presentation.websocket.AccessWebSocketHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * Job programado para el procesamiento batch de archivos CSV.
 * Soporta reprogramación dinámica del horario via API.
 */
@Component
@Slf4j
public class BatchProcessingJob {

    private final AccessLogReader logReader;
    private final AccessRepository accessRepository;
    private final UserRegistryPort userRegistryPort;
    private final JpaUserRepository jpaUserRepository;
    private final TaskScheduler taskScheduler;
    private final CsvAccessLogWriter csvAccessLogWriter;
    private final DatabaseBackupService databaseBackupService;

    @Autowired
    @Lazy
    private AccessWebSocketHandler webSocketHandler;

    @Value("${operation.start-hour:8}")
    private int operationStartHour;

    @Value("${operation.end-hour:22}")
    private int operationEndHour;

    @Value("${batch.cron.expression:0 0 22 * * *}")
    @Getter
    private String currentCronExpression;

    // Referencia a la tarea programada actual (para poder cancelarla al
    // reprogramar)
    private ScheduledFuture<?> scheduledTask;

    // --- Tracking de la última ejecución ---
    @Getter
    private LocalDateTime lastRunTime;
    @Getter
    private int lastRunRecords;
    @Getter
    private boolean lastRunSuccess;
    @Getter
    private String lastRunError;
    @Getter
    private String scheduledTimeDisplay;

    public BatchProcessingJob(AccessLogReader logReader,
            AccessRepository accessRepository,
            UserRegistryPort userRegistryPort,
            JpaUserRepository jpaUserRepository,
            TaskScheduler taskScheduler,
            CsvAccessLogWriter csvAccessLogWriter,
            DatabaseBackupService databaseBackupService) {
        this.logReader = logReader;
        this.accessRepository = accessRepository;
        this.userRegistryPort = userRegistryPort;
        this.jpaUserRepository = jpaUserRepository;
        this.taskScheduler = taskScheduler;
        this.csvAccessLogWriter = csvAccessLogWriter;
        this.databaseBackupService = databaseBackupService;
    }

    /**
     * Inicializa la programación del batch al arrancar la aplicación.
     */
    @PostConstruct
    public void init() {
        scheduleTask(currentCronExpression);
        log.info("Batch programado con cron: {}", currentCronExpression);
    }

    /**
     * Programa o reprograma el batch con una nueva expresión cron.
     *
     * @param cronExpression Expresión cron de Spring (6 campos)
     */
    public synchronized void scheduleTask(String cronExpression) {
        // Cancelar la tarea anterior si existe
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            log.info("Tarea batch anterior cancelada");
        }

        this.currentCronExpression = cronExpression;
        this.scheduledTimeDisplay = cronToReadableTime(cronExpression);

        // Programar nueva tarea
        scheduledTask = taskScheduler.schedule(this::processDailyBatch, new CronTrigger(cronExpression));
        log.info("Batch reprogramado: {} ({})", cronExpression, scheduledTimeDisplay);
    }

    /**
     * Reprograma el batch para ejecutarse a una hora específica.
     *
     * @param hour   Hora (0-23)
     * @param minute Minuto (0-59)
     * @return La nueva expresión cron configurada
     */
    public String reschedule(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("Hora inválida: " + hour + ":" + minute);
        }
        String cron = String.format("0 %d %d * * *", minute, hour);
        scheduleTask(cron);
        return cron;
    }

    /**
     * Proceso batch principal.
     */
    public void processDailyBatch() {
        log.info("=== INICIANDO PROCESO BATCH ===");
        log.info("Hora de ejecución: {}", LocalDateTime.now());

        notifyBatchStarted();

        try {
            // 1. Sincronizar usuarios CSV → MySQL
            syncUserRegistry();

            // 2. ROTAR el CSV activo: cerrar el writer para que el archivo
            // sea incluido en getPendingFiles() (no se excluye si está cerrado)
            String rotatedFile = null;
            if (csvAccessLogWriter.isReady()) {
                rotatedFile = csvAccessLogWriter.getCurrentFilePath();
                log.info("Rotando CSV activo: {}", rotatedFile);
                csvAccessLogWriter.flush();
                csvAccessLogWriter.close();
                log.info("Writer cerrado. El archivo ahora será procesado por el batch.");
            }

            // 3. Obtener archivos pendientes (ahora incluye el recién cerrado)
            List<Path> pendingFiles = logReader.getPendingFiles();

            if (pendingFiles.isEmpty()) {
                log.warn("No hay archivos CSV pendientes para procesar.");
                updateLastRun(0, true, "Sin archivos pendientes");
                notifyBatchCompleted(0, 0, true);
                reopenCsvWriter();
                return;
            }

            log.info("Archivos a procesar: {}", pendingFiles.size());
            for (Path f : pendingFiles) {
                log.info("  -> {}", f.toAbsolutePath());
            }

            int totalProcessed = 0;
            int totalErrors = 0;

            for (Path file : pendingFiles) {
                try {
                    int processed = processFile(file);
                    totalProcessed += processed;
                    log.info("Archivo procesado: {} ({} registros guardados en MySQL)", file.getFileName(), processed);
                } catch (Exception e) {
                    totalErrors++;
                    log.error("Error procesando archivo {}: {}", file.getFileName(), e.getMessage(), e);
                }
            }

            log.info("=== PROCESO BATCH COMPLETADO ===");
            log.info("Total registros insertados en MySQL: {}", totalProcessed);
            log.info("Archivos con errores: {}", totalErrors);

            updateLastRun(totalProcessed, totalErrors == 0,
                    totalErrors > 0 ? totalErrors + " archivos con errores" : null);
            notifyBatchCompleted(totalProcessed, totalErrors, totalErrors == 0);

            // 4. Ejecutar backup en MySQL via stored procedure
            try {
                int backupCount = databaseBackupService.executeBackup();
                log.info("Backup MySQL completado: {} registros respaldados", backupCount);
            } catch (Exception e) {
                log.error("Error en backup MySQL: {}", e.getMessage());
            }

            // 5. Reabrir el CSV writer con un nuevo archivo para seguir escribiendo
            reopenCsvWriter();

        } catch (Exception e) {
            log.error("Error fatal en proceso batch: {}", e.getMessage(), e);
            updateLastRun(0, false, e.getMessage());
            notifyBatchCompleted(0, 1, false);
            reopenCsvWriter();
        }
    }

    /**
     * Reabre el CSV writer con un nombre nuevo para continuar la sesión.
     */
    private void reopenCsvWriter() {
        try {
            String sessionName = "batch_" + LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
            String newPath = csvAccessLogWriter.initialize(sessionName);
            log.info("CSV writer reabierto: {}", newPath);
        } catch (Exception e) {
            log.error("Error reabriendo CSV writer: {}", e.getMessage(), e);
        }
    }

    private void syncUserRegistry() {
        log.info("Sincronizando registro de usuarios con MySQL...");
        try {
            List<RegisteredUser> localUsers = userRegistryPort.findAll();
            log.info("Usuarios en CSV local: {}", localUsers.size());
            for (RegisteredUser u : localUsers) {
                log.info("  CSV -> UID: {}, Nombre: {}", u.getUid(), u.getName());
            }

            int synced = 0;
            int skipped = 0;
            for (RegisteredUser user : localUsers) {
                try {
                    boolean exists = jpaUserRepository.existsByNfcUid(user.getUid());
                    if (!exists) {
                        UserEntity entity = UserEntity.builder()
                                .nfcUid(user.getUid())
                                .userName(user.getName())
                                .role("usuario")
                                .createdAt(user.getRegisteredAt())
                                .build();
                        jpaUserRepository.save(entity);
                        synced++;
                        log.info("Usuario INSERTADO en MySQL: {} ({})", user.getUid(), user.getName());
                    } else {
                        skipped++;
                        log.debug("Usuario ya existe en MySQL: {}", user.getUid());
                    }
                } catch (Exception e) {
                    log.error("Error sincronizando usuario {}: {}", user.getUid(), e.getMessage(), e);
                }
            }
            log.info("Sincronización completada: {} insertados, {} ya existían", synced, skipped);
        } catch (Exception e) {
            log.error("Error sincronizando usuarios: {}", e.getMessage(), e);
        }
    }

    private int processFile(Path filePath) {
        log.info("Procesando archivo: {}", filePath.toAbsolutePath());
        List<AccessRecord> records = logReader.readFromFile(filePath);

        if (records.isEmpty()) {
            log.warn("Archivo vacío o sin registros válidos: {}", filePath);
            logReader.moveToHistory(filePath);
            return 0;
        }

        log.info("Leídos {} registros del CSV, insertando en MySQL...", records.size());
        int saved = accessRepository.saveAll(records);
        log.info("Insertados {} registros en MySQL desde {}", saved, filePath.getFileName());

        logReader.moveToHistory(filePath);
        return saved;
    }

    public boolean isWithinOperationHours() {
        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.of(operationStartHour, 0);
        LocalTime end = LocalTime.of(operationEndHour, 0);
        return !now.isBefore(start) && now.isBefore(end);
    }

    public int runManualBatch() {
        log.info("Ejecutando proceso batch manualmente...");

        notifyBatchStarted();

        List<Path> pendingFiles = logReader.getPendingFiles();
        int totalProcessed = 0;
        int totalErrors = 0;

        for (Path file : pendingFiles) {
            try {
                totalProcessed += processFile(file);
            } catch (Exception e) {
                totalErrors++;
                log.error("Error en batch manual para {}: {}", file, e.getMessage());
            }
        }

        updateLastRun(totalProcessed, totalErrors == 0, totalErrors > 0 ? totalErrors + " archivos con errores" : null);
        notifyBatchCompleted(totalProcessed, totalErrors, totalErrors == 0);

        return totalProcessed;
    }

    // --- Helpers ---

    private void updateLastRun(int records, boolean success, String error) {
        this.lastRunTime = LocalDateTime.now();
        this.lastRunRecords = records;
        this.lastRunSuccess = success;
        this.lastRunError = error;
    }

    private void notifyBatchStarted() {
        try {
            if (webSocketHandler != null) {
                webSocketHandler.broadcastBatchStarted();
            }
        } catch (Exception e) {
            log.debug("No se pudo notificar inicio de batch: {}", e.getMessage());
        }
    }

    private void notifyBatchCompleted(int records, int errors, boolean success) {
        try {
            if (webSocketHandler != null) {
                webSocketHandler.broadcastBatchCompleted(records, errors, success);
            }
        } catch (Exception e) {
            log.debug("No se pudo notificar fin de batch: {}", e.getMessage());
        }
    }

    /**
     * Convierte una expresión cron a texto legible (e.g. "22:00").
     */
    private String cronToReadableTime(String cron) {
        try {
            String[] parts = cron.split(" ");
            int minute = Integer.parseInt(parts[1]);
            int hour = Integer.parseInt(parts[2]);
            return String.format("%02d:%02d", hour, minute);
        } catch (Exception e) {
            return cron;
        }
    }
}
