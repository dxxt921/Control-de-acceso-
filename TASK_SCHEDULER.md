# TaskScheduler en el Sistema IoT de Control de Acceso

## Descripción General

El **TaskScheduler** de Spring es el mecanismo central que permite a nuestra aplicación ejecutar tareas programadas de forma automática sin necesidad de intervención humana ni de una segunda aplicación externa. En nuestro sistema, se utiliza principalmente en la clase `BatchProcessingJob` para orquestar el procesamiento diario de archivos CSV hacia MySQL, la sincronización de usuarios y los respaldos de base de datos.

---

## ¿Por qué un TaskScheduler interno y no una segunda aplicación?

### El problema con una segunda aplicación

Si el procesamiento batch se realizara mediante un programa independiente (por ejemplo, un segundo `.jar`, un script de Python o un cron job del sistema operativo), surgirían múltiples problemas:

| Problema | Con segunda app | Con TaskScheduler integrado |
|----------|----------------|-----------------------------|
| **Acceso a datos en memoria** | ❌ No puede saber qué CSV está siendo escrito en ese momento | ✅ Accede directamente al `CsvAccessLogWriter` para saber cuál es el archivo activo |
| **Coordinación de archivos** | ❌ Podría leer un CSV a medio escribir, causando datos corruptos | ✅ Cierra el writer, procesa el archivo y reabre uno nuevo de forma atómica |
| **Estado compartido** | ❌ Necesitaría IPC, sockets o archivos de señalización | ✅ Comparte beans de Spring: servicios, repositorios, WebSocket handler |
| **Notificaciones en tiempo real** | ❌ No puede enviar mensajes al Dashboard por WebSocket | ✅ Llama directamente a `webSocketHandler.broadcastBatchCompleted()` |
| **Configuración** | ❌ Duplicar credenciales de BD, rutas de archivos, etc. | ✅ Un solo `application.properties` para todo |
| **Despliegue** | ❌ Coordinar inicio/parada de 2 procesos | ✅ Un solo `java -jar`, todo arranca junto |
| **Reprogramación dinámica** | ❌ Requiere reiniciar el cron o modificar archivos externos | ✅ Se reprograma en caliente desde el Dashboard vía API REST |
| **Monitoreo** | ❌ Necesitaría un mecanismo propio de health checks | ✅ El `SystemStatusController` expone el estado del batch en un solo JSON |

### Ventaja clave: coherencia de datos

El punto más crítico es la **coherencia al procesar archivos CSV**. El `BatchProcessingJob` necesita:

1. **Saber cuál es el CSV activo** para no procesarlo mientras se escribe.
2. **Cerrar el writer** antes de procesar, para no perder datos.
3. **Reabrir el writer** con un nuevo archivo después de procesar.

Todo esto es posible porque el batch vive **dentro del mismo proceso JVM** que el servicio de escritura CSV:

```java
// Dentro de processDailyBatch() — líneas 149-158 del BatchProcessingJob
if (csvAccessLogWriter.isReady()) {
    rotatedFile = csvAccessLogWriter.getCurrentFilePath();
    log.info("Rotando CSV activo: {}", rotatedFile);
    csvAccessLogWriter.flush();       // Vaciar buffer a disco
    csvAccessLogWriter.close();       // Cerrar el archivo
    log.info("Writer cerrado. El archivo ahora será procesado por el batch.");
}
```

Una segunda aplicación **jamás podría hacer esto** sin un mecanismo de comunicación interprocesos complejo (sockets, archivos de lock, señales del sistema operativo, etc.), lo cual agregaría fragilidad al sistema.

---

## Cómo funciona paso a paso

### Paso 1: Inicio de la aplicación — `@PostConstruct`

Cuando Spring Boot arranca, el bean `BatchProcessingJob` se inicializa automáticamente. El método `init()` se ejecuta gracias a la anotación `@PostConstruct`:

```java
@PostConstruct
public void init() {
    scheduleTask(currentCronExpression);
    log.info("Batch programado con cron: {}", currentCronExpression);
}
```

Esto registra la tarea batch con la expresión cron definida en `application.properties`:

```properties
batch.cron.expression=0 0 22 * * *    # Todos los días a las 22:00
```

### Paso 2: Programación con `TaskScheduler` + `CronTrigger`

El método `scheduleTask()` utiliza el `TaskScheduler` de Spring para registrar la tarea:

```java
public synchronized void scheduleTask(String cronExpression) {
    // 1. Cancelar tarea anterior si existe
    if (scheduledTask != null) {
        scheduledTask.cancel(false);
    }

    this.currentCronExpression = cronExpression;
    this.scheduledTimeDisplay = cronToReadableTime(cronExpression);

    // 2. Programar nueva tarea con CronTrigger
    scheduledTask = taskScheduler.schedule(
        this::processDailyBatch,     // Método a ejecutar
        new CronTrigger(cronExpression)  // Cuándo ejecutarlo
    );
}
```

**¿Cómo funciona internamente?**

1. `TaskScheduler` es un bean que Spring crea automáticamente cuando la clase principal tiene `@EnableScheduling`.
2. `CronTrigger` interpreta la expresión cron de 6 campos (segundo, minuto, hora, día, mes, día-semana).
3. `schedule()` devuelve un `ScheduledFuture<?>`, que es una referencia a la tarea para poder cancelarla después.
4. `synchronized` garantiza que no haya condiciones de carrera si se reprograma desde 2 hilos simultáneos.

### Paso 3: Reprogramación dinámica desde el Dashboard

El usuario puede cambiar la hora del batch en caliente desde la interfaz web, **sin reiniciar la aplicación**:

```
POST /api/system/batch/schedule
Body: { "hour": 23, "minute": 30 }
```

Esto llama a `reschedule()`:

```java
public String reschedule(int hour, int minute) {
    // Validar entrada
    if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
        throw new IllegalArgumentException("Hora inválida");
    }
    // Construir nueva expresión cron
    String cron = String.format("0 %d %d * * *", minute, hour);
    // Reprogramar (cancela la anterior + registra la nueva)
    scheduleTask(cron);
    return cron;
}
```

El flujo completo es:

```
Dashboard → API REST → reschedule(23, 30)
  → scheduleTask("0 30 23 * * *")
    → scheduledTask.cancel()        // Cancela ejecución de las 22:00
    → taskScheduler.schedule(...)   // Registra ejecución a las 23:30
```

### Paso 4: Ejecución del batch — `processDailyBatch()`

Cuando el cron se activa (o el usuario ejecuta manualmente), se ejecuta el proceso completo:

```
┌────────────────────────────────────────────────────────┐
│              PROCESO BATCH DIARIO                      │
├────────────────────────────────────────────────────────┤
│                                                        │
│  1. WebSocket: "BATCH_STARTED" → Dashboard             │
│     │                                                  │
│  2. SINCRONIZAR USUARIOS                               │
│     │  user_registry.csv → MySQL (tabla users)         │
│     │  Por cada usuario en CSV:                        │
│     │    ¿Existe en MySQL? → Skip                      │
│     │    ¿No existe? → INSERT                          │
│     │                                                  │
│  3. ROTAR CSV ACTIVO                                   │
│     │  csvAccessLogWriter.flush()  → vaciar buffer     │
│     │  csvAccessLogWriter.close()  → cerrar archivo    │
│     │  (ahora el CSV queda "libre" para procesar)      │
│     │                                                  │
│  4. BUSCAR CSVs PENDIENTES                             │
│     │  Listar ./data_logs/*.csv                        │
│     │  Excluir user_registry*.csv                      │
│     │  (ya no excluye el activo porque fue cerrado)    │
│     │                                                  │
│  5. PROCESAR CADA CSV                                  │
│     │  Para cada archivo:                              │
│     │    a) Leer con OpenCSV (skip header)             │
│     │    b) Parsear: timestamp,uid,status,station_id   │
│     │    c) INSERT en MySQL (access_logs)              │
│     │    d) Mover a ./history/                         │
│     │                                                  │
│  6. BACKUP MySQL                                       │
│     │  CALL sp_backup_access_logs()                    │
│     │  (copia access_logs → access_logs_backup)        │
│     │                                                  │
│  7. REABRIR CSV WRITER                                 │
│     │  csvAccessLogWriter.initialize("batch_HHmmss")   │
│     │  (nuevo archivo CSV para seguir escribiendo)     │
│     │                                                  │
│  8. WebSocket: "BATCH_COMPLETED" → Dashboard           │
│     │  (con: registros procesados, errores, éxito)     │
│                                                        │
└────────────────────────────────────────────────────────┘
```

### Paso 5: Procesamiento individual de archivos

El método `processFile()` maneja un CSV individual:

```java
private int processFile(Path filePath) {
    // 1. Leer todos los registros del CSV
    List<AccessRecord> records = logReader.readFromFile(filePath);

    // 2. Si está vacío, mover a history y retornar
    if (records.isEmpty()) {
        logReader.moveToHistory(filePath);
        return 0;
    }

    // 3. Insertar TODOS los registros en MySQL de golpe
    int saved = accessRepository.saveAll(records);

    // 4. Mover el CSV procesado a ./history/
    logReader.moveToHistory(filePath);

    return saved;
}
```

Si el archivo CSV no existe, `CsvAccessLogReader` automáticamente busca en `./data_logs_backup/` el archivo con sufijo `_backup.csv`, demostrando resiliencia incluso durante el batch.

### Paso 6: Sincronización de usuarios

Antes de procesar los CSVs, se sincronizan los usuarios del CSV local con MySQL:

```java
private void syncUserRegistry() {
    List<RegisteredUser> localUsers = userRegistryPort.findAll();

    for (RegisteredUser user : localUsers) {
        if (!jpaUserRepository.existsByNfcUid(user.getUid())) {
            // No existe en MySQL → INSERT
            UserEntity entity = UserEntity.builder()
                .nfcUid(user.getUid())
                .userName(user.getName())
                .role("usuario")
                .createdAt(user.getRegisteredAt())
                .build();
            jpaUserRepository.save(entity);
        }
        // Si ya existe → skip
    }
}
```

Esto garantiza que cuando los registros de acceso se insertan en MySQL, ya existen los usuarios correspondientes para consultas relacionales.

---

## Ejecución manual vs automática

El sistema soporta **dos formas** de disparar el batch:

| Característica | Automática (Cron) | Manual (API) |
|----------------|-------------------|--------------|
| Trigger | `CronTrigger` a las 22:00 | `POST /api/batch/run` |
| Método | `processDailyBatch()` | `runManualBatch()` |
| Rota CSV activo | ✅ Sí (cierra y reabre) | ❌ No (solo procesa archivos ya cerrados) |
| Sync usuarios | ✅ Sí | ❌ No |
| Backup MySQL | ✅ Sí | ❌ No |
| WebSocket notif | ✅ Sí | ✅ Sí |

El batch manual es útil para pruebas o para forzar un procesamiento inmediato sin esperar al cron.

---

## Otras tareas programadas en la aplicación

Además del `BatchProcessingJob`, el TaskScheduler de Spring maneja:

### `CsvResilienceService` — Verificación cada 30 segundos

```java
@Scheduled(fixedRate = 30000)  // Cada 30 segundos
public void checkFileIntegrity() {
    // Verifica que el CSV activo exista en disco
    // Si fue eliminado, lo restaura desde el backup
    // Si no hay backup, crea uno nuevo
}
```

Esta tarea corre en un hilo separado gracias al `TaskScheduler` y protege contra eliminaciones accidentales del CSV activo.

### Habilitación: `@EnableScheduling`

Todo esto funciona porque la clase principal tiene:

```java
@SpringBootApplication
@EnableScheduling     // ← Activa el TaskScheduler de Spring
public class IoTAccessApplication {
    public static void main(String[] args) {
        SpringApplication.run(IoTAccessApplication.class, args);
    }
}
```

Sin esta anotación, ni el `@Scheduled` del resilience service ni el `TaskScheduler` inyectado en `BatchProcessingJob` funcionarían.

---

## Diagrama de arquitectura del TaskScheduler

```
┌─────────────────────────────────────────────────────────────┐
│                    SPRING BOOT APPLICATION                   │
│                    @EnableScheduling                         │
│                                                             │
│   ┌─────────────────┐     ┌──────────────────────────────┐ │
│   │  TaskScheduler  │     │     BatchProcessingJob       │ │
│   │  (Thread Pool)  │────▶│                              │ │
│   │                 │     │  ┌─ processDailyBatch()      │ │
│   │  Cron: 22:00    │     │  │   1. syncUserRegistry()   │ │
│   │                 │     │  │   2. csvWriter.close()     │ │
│   └────────┬────────┘     │  │   3. getPendingFiles()     │ │
│            │              │  │   4. processFile() x N     │ │
│            │ @Scheduled   │  │   5. dbBackup.execute()    │ │
│            │ fixedRate     │  │   6. csvWriter.reopen()    │ │
│            ▼              │  └─ reschedule(hora, min)     │ │
│   ┌─────────────────┐     └──────────────┬───────────────┘ │
│   │CsvResilience    │                    │                  │
│   │Service          │                    │ WebSocket        │
│   │                 │                    ▼                  │
│   │ Cada 30s:       │     ┌──────────────────────────────┐ │
│   │ checkFile       │     │  AccessWebSocketHandler      │ │
│   │ Integrity()     │     │  broadcastBatchStarted()     │ │
│   └─────────────────┘     │  broadcastBatchCompleted()   │ │
│                           └──────────────────────────────┘ │
│                                      │                      │
└──────────────────────────────────────┼──────────────────────┘
                                       │ ws://localhost:8080
                                       ▼
                              ┌─────────────────┐
                              │    Dashboard     │
                              │   (Navegador)    │
                              └─────────────────┘
```

---

## Resumen técnico

| Aspecto | Implementación |
|---------|----------------|
| **Clase principal** | `BatchProcessingJob` (361 líneas) |
| **Mecanismo** | `TaskScheduler` + `CronTrigger` de Spring |
| **Activación** | `@EnableScheduling` en `IoTAccessApplication` |
| **Configuración** | `batch.cron.expression` en `application.properties` |
| **Reprogramación** | Dinámica vía API REST sin reiniciar |
| **Tracking interno** | `ScheduledFuture<?>` para cancelar/reprogramar |
| **Concurrencia** | `synchronized` en `scheduleTask()` |
| **Notificaciones** | WebSocket (`BATCH_STARTED`, `BATCH_COMPLETED`) |
| **Resiliencia** | `CsvResilienceService` con `@Scheduled(fixedRate=30000)` |
| **Ventaja sobre app externa** | Acceso directo a beans, sin IPC, sin configuración duplicada |
