# Documentación Semanal: IoT Access System (9/02/26 - 16/02/26)

## 1. Descripción del Avance Planeado
Para esta semana, el objetivo principal fue la integración completa del sistema de persistencia y la lógica de administración. Específicamente:
*   **Implementación del módulo de procesamiento Batch (`BatchProcessingJob`)**: Automatización de la carga de logs desde archivos CSV locales hacia la base de datos central MySQL.
*   **Sincronización de Usuarios**: Mecanismo para asegurar que los usuarios registrados localmente (en CSV/memoria) se reflejen correctamente en la base de datos relacional.
*   **Interfaz de Administración**: Finalización del dashboard web con capacidad de gestión de dispositivos (altas y bajas) y visualización de estadísticas en tiempo real.
*   **Robustez del Sistema Embebido**: Implementación de mecanismos de recuperación ante fallos de I2C en el Arduino y flujo de autorización de administrador.

---

## 2. Descripción Esquemática de la Arquitectura

### Manejo de Archivos (File Handling)
El sistema utiliza una arquitectura híbrida para el manejo de datos, priorizando la disponibilidad local y la consistencia eventual:
1.  **Captura en Tiempo Real**: La clase `CsvAccessLogWriter` actúa como un *buffer* persistente. Cada lectura de acceso se escribe inmediatamente en un archivo CSV en la carpeta `./data_logs`.
2.  **Rotación de Archivos**: Los archivos se nombran con el patrón `session_{timestamp}.csv`. Al finalizar una sesión o ejecutarse el batch, el archivo activo se cierra y rota, asegurando que no haya bloqueos de escritura.
3.  **Procesamiento y Archivado**: El `BatchProcessingJob` lee los archivos cerrados, los procesa (inserta en MySQL) y, tras un procesamiento exitoso, los mueve a la carpeta `./history` para auditoría y respaldo.

### Configuración del Puerto Serial
La comunicación con el hardware (Arduino + PN532) se gestiona mediante la librería `jSerialComm`:
*   **Detección Automática**: El servicio `SerialPortScanner` escanea los puertos COM disponibles y expone sus metadatos (Nombre, Descripción, Fabricante) en la interfaz web.
*   **Configuración**:
    *   **Baud Rate**: 115200 (para minimizar latencia).
    *   **Data Bits**: 8
    *   **Stop Bits**: 1
    *   **Parity**: None
*   **Protocolo**: Se utiliza un protocolo de mensajes en texto simple (e.g., `UID:XX-XX...`, `P` para ping, `K:Nombre` para confirmación de registro).

### Aplicación de Segundo Plano (Background Task)
*   **Nombre del Componente**: `BatchProcessingJob.java`
*   **Ejecución**: Tarea programada mediante Spring Scheduler (`@Scheduled`).
*   **Horario**: Configurado por defecto a las **10:00 PM (22:00)** todos los días (`0 0 22 * * *`).
*   **Responsabilidad**:
    1.  Sincronizar usuarios nuevos desde el registro local a la tabla `users`.
    2.  Leer todos los archivos CSV pendientes en `./data_logs`.
    3.  Insertar masivamente los registros en la tabla `access_logs`.
    4.  Mover archivos procesados a `./history`.

---

## 3. Descripción del Sistema de Base de Datos

### Detalles del Servidor
*   **Motor**: MySQL 8.0 / MariaDB
*   **Host**: `localhost:3306`
*   **Base de Datos**: `smart_access_db`
*   **Usuario**: `root`
*   **Timezone**: `America/Mexico_City`

### Estructura del Modelo de Datos (Diagrama ER Simplificado)

1.  **Tabla `users`**: Almacena la identidad de los usuarios autorizados.
    *   `id` (PK, Auto Inc): Identificador interno.
    *   `nfc_uid` (Unique, Varchar): El ID físico de la tarjeta/tag.
    *   `user_name` (Varchar): Nombre legible del usuario.
    *   `role` (Varchar): Rol (e.g., 'usuario', 'admin').
    *   `created_at` (Datetime): Fecha de registro.

2.  **Tabla `access_logs`**: Bitácora histórica de todos los intentos de acceso.
    *   `id` (PK, Auto Inc): Identificador del evento.
    *   `uid_detected` (Varchar): UID leído en el evento.
    *   `access_timestamp` (Datetime): Fecha y hora exacta.
    *   `access_granted` (Boolean): `TRUE` si se abrió el torniquete.
    *   `station_id` (Int): ID de la estación (puerta).

3.  **Tabla `stations`**: Catálogo de puntos de acceso.
    *   `id` (PK): Identificador manual (e.g., 1).
    *   `location_name` (Varchar): Descripción (e.g., "Entrada Principal").

### Script de Creación SQL

```sql
CREATE DATABASE IF NOT EXISTS smart_access_db;
USE smart_access_db;

-- Tabla de Usuarios
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nfc_uid VARCHAR(50) NOT NULL UNIQUE,
    user_name VARCHAR(100) NOT NULL,
    role VARCHAR(50) DEFAULT 'usuario',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tabla de Estaciones
CREATE TABLE IF NOT EXISTS stations (
    id INT PRIMARY KEY,
    location_name VARCHAR(100) NOT NULL
);

-- Tabla de Logs de Acceso
CREATE TABLE IF NOT EXISTS access_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uid_detected VARCHAR(50) NOT NULL,
    access_timestamp DATETIME NOT NULL,
    access_granted BOOLEAN NOT NULL,
    station_id INT,
    FOREIGN KEY (station_id) REFERENCES stations(id)
);

-- Datos iniciales
INSERT INTO stations (id, location_name) VALUES (1, 'Entrada Principal') 
ON DUPLICATE KEY UPDATE location_name='Entrada Principal';
```

*(Incluir aquí captura de pantalla de MySQL Workbench o terminal mostrando las tablas creadas)*

---

## 4. Código del Sistema Embebido (Arduino)

### Descripción de Funcionamiento
El firmware (`access_control_with_admin.ino`) implementa una máquina de estados finitos que gestiona el hardware localmente para garantizar velocidad de respuesta.
*   **Estado NORMAL**: Espera lectura de tarjeta NFC. Si lee una, envía el UID por serial a Java y espera respuesta (`1`=Abrir, `0`=Denegar).
*   **Estado ESPERANDO_ADMIN**: Se activa con comando `W` desde Java. Espera específicamente la tarjeta maestra para autorizar un registro.
*   **Estado REGISTRO**: Se activa con comando `E` tras validar al admin. El siguiente tag leído se enviará como "nuevo usuario" para registro.
*   **Recuperación I2C**: Función crítica `recoverI2CBus()` que detecta bloqueos en el bus (común con servos y pantallas I2C) y lo reinicia automáticamente enviando pulsos de reloj manuales.

### Código Fuente (Fragmento Principal)
*Ver archivo completo adjunto: `arduino/access_control_with_admin.ino`*

```cpp
// Fragmento de la lógica principal en el loop
void loop() {
  // 1. Procesar comandos desde la PC (Java)
  while (Serial.available() > 0) {
    // ... lectura de comandos (A, W, E, 1, 0) ...
  }

  // 2. Lectura NFC con Cooldown (evita lecturas dobles)
  if (millis() - ultimaLectura < TAG_COOLDOWN_MS) return;

  if (nfc.readPassiveTargetID(PN532_MIFARE_ISO14443A, uid, &uidLength, 100)) {
    // ... Enviar UID por Serial ...
    Serial.print("UID:");
    // ... formateo hexadecimal ...
  }

  // 3. Auto-recuperación ante fallos del bus I2C
  if (Wire.getWireTimeoutFlag()) {
    reiniciarI2C(); // Reinicia LCD y PN532
  }
}
```

---

## 5. Código de la Interfaz (Java Spring Boot)

### Descripción de Funcionamiento
La interfaz web se sirve mediante **Thymeleaf** (renderizado en servidor) y se actualiza dinámicamente con **WebSockets** y **Tailwind CSS**.
*   **DashboardController**: Gestiona la vista principal `/dashboard`. Inyecta los datos iniciales (estadísticas del día, últimos registros) en el modelo HTML.
*   **Comunicación Real-time**: Un cliente WebSocket JS (`dashboard.html`) se conecta a `/ws/access`. Cuando el servidor recibe un evento del Arduino, lo retransmite inmediatamente a todos los navegadores conectados para actualizar la tabla y gráficas sin recargar la página.
*   **Diseño**: Uso de "Glassmorphism" (fondos semitransparentes con desenfoque) y gradientes oscuros para una estética moderna y profesional.

### Código Fuente (Controlador Principal)

```java
@Controller
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final AccessService accessService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Carga de datos iniciales para la vista
        SessionStatusDto status = accessService.getSessionStatus();
        List<AccessRecordDto> records = accessService.getLatestRecords(50);
        AccessService.DayStatsDto stats = accessService.getDayStats();

        model.addAttribute("sessionStatus", status);
        model.addAttribute("records", records);
        model.addAttribute("stats", stats);
        
        return "dashboard"; // Retorna template dashboard.html
    }
    
    // ... Endpoints API para AJAX y control de hardware ...
}
```

*(Incluir aquí captura de las pantallas: Dashboard Principal, Modal de Configuración, Modal de Registro de Usuario)*

---

## 6. Descripción de la Aplicación de Segundo Plano

### Descripción y Código
La aplicación de segundo plano es el **Job de Procesamiento Batch** integrado en el mismo servicio Spring Boot. Aprovecha la capacidad multihilo de Java para ejecutarse sin interrumpir la operación de control de acceso.

**Clase**: `com.iotaccess.application.scheduler.BatchProcessingJob`

**Lógica Clave**:
1.  **Trigger**: Se activa por Cron (`@Scheduled`).
2.  **Sincronización**:
    *   Recorre todos los usuarios en memoria/CSV.
    *   Verifica si existen en la BD MySQL (`jpaUserRepository.existsByNfcUid`).
    *   Si no existen, los inserta.
3.  **Procesamiento de Logs**:
    *   Obtiene la lista de archivos CSV cerrados.
    *   Por cada archivo, lee los registros y los convierte a entidades `AccessLogEntity`.
    *   Usa `accessRepository.saveAll()` para inserción eficiente por lotes.
    *   Mueve el archivo a la carpeta de histórico si todo fue exitoso.

```java
@Component
@Slf4j
public class BatchProcessingJob {

    // ... dependencias ...

    @PostConstruct
    public void init() {
        // Programación dinámica basada en configuración
        scheduleTask(currentCronExpression);
    }

    public void processDailyBatch() {
        log.info("=== INICIANDO PROCESO BATCH ===");
        
        // 1. Sincronizar usuarios (CSV -> MySQL)
        syncUserRegistry();

        // 2. Rotar archivo actual para procesarlo también
        if (csvAccessLogWriter.isReady()) {
            csvAccessLogWriter.close(); // Fuerza cierre y rotación
        }

        // 3. Procesar archivos pendientes
        List<Path> pendingFiles = logReader.getPendingFiles();
        for (Path file : pendingFiles) {
             processFile(file); // Lee CSV -> Inserta en MySQL -> Mueve a History
        }
        
        // 4. Reabrir writer para seguir operando
        reopenCsvWriter();
    }
}
```
