# Diccionario de Clases del Sistema IoT de Control de Acceso

A continuación se presenta un resumen de todas las clases Java (`.java`) del proyecto agrupadas por su capa o módulo de arquitectura de software, junto con una breve explicación de su responsabilidad en el sistema.

## 1. Clase Principal (Entry Point)
| Clase | Descripción |
|---|---|
| `IoTAccessApplication.java` | Clase principal que arranca la aplicación Spring Boot y habilita el `TaskScheduler` (`@EnableScheduling`). |

## 2. Capa de Presentación (Controllers y WebSockets)
Contiene los puntos de entrada HTTP (API REST) y WebSockets para la comunicación en tiempo real con el dashboard/frontend.

| Clase | Descripción |
|---|---|
| `DashboardController.java` | Controlador MVC que sirve la página web principal y vistas del dashboard. |
| `ConfigController.java` | Controlador REST para manejar la configuración del sistema (como modificar la hora del batch). |
| `DeviceController.java` | Controlador REST para administrar los dispositivos, solicitar conexiones a puertos seriales y enviar comandos al Arduino. |
| `SystemStatusController.java` | Controlador REST que provee el estado general del sistema (puertos activos, CSV abierto, estado del batch) en formato JSON. |
| `AccessWebSocketHandler.java` | Manejador de WebSockets que envía notificaciones en tiempo real al frontend (ej. acceso concedido, fallido, desconexiones, inicio/fin del batch). |
| `WebSocketConfig.java` | Clase de configuración de Spring que registra el componente WebSocket y define su endpoint (`/ws`). |

## 3. Capa de Aplicación (Servicios, DTOs y Tareas Programadas)
Contiene la lógica de negocio, orquestación, objetos de transferencia de datos y tareas automáticas.

| Clase | Descripción |
|---|---|
| `AccessServiceImpl.java` | Componente central (Servicio) que orquesta la lógica de un lector NFC: decide qué hacer cuando se escanea una tarjeta (acceder o registrar). |
| `AccessService.java` | Interfaz (contrato) del servicio de acceso (`AccessServiceImpl`). |
| `IdentityServiceImpl.java` | Servicio encargado de gestionar el registro temporal o permanente de nuevas tarjetas/usuarios. |
| `IdentityService.java` | Interfaz del servicio de identidades (`IdentityServiceImpl`). |
| `DeviceManagementService.java` | Servicio encargado de gestionar el ciclo de vida de la conexión y desconexión con el hardware (Arduino). |
| `BatchProcessingJob.java` | Tarea programada (`TaskScheduler`) que sincroniza usuarios, procesa los CSV a la base de datos MySQL, rota archivos y hace el backup diario. |
| `AccessRecordDto.java` | Objeto de transferencia de datos que representa un registro de acceso enviado hacia el frontend. |
| `EnrollmentStateDto.java` | Objeto de transferencia de datos que encapsula si el sistema está en modo "registro de usuarios" y sus detalles. |
| `RegisteredUserDto.java` | Objeto de transferencia de datos que viaja al frontend con la información de un usuario registrado. |
| `SessionStatusDto.java` | Objeto de transferencia de datos con el resumen del estado actual de los puertos o sistema global. |

## 4. Capa de Dominio (Modelos, Excepciones y Puertos)
Contiene las entidades nativas de negocio (reglas puras) e interfaces (ports) que la infraestructura debe implementar.

| Clase | Descripción |
|---|---|
| `AccessRecord.java` | Modelo de negocio para un registro individual de acceso (marca de tiempo, UID, estado, etc.). |
| `AccessStatus.java` | Enumerador que define si un acceso fue concedido (`GRANTED`) o denegado (`DENIED`). |
| `RegisteredUser.java` | Modelo de negocio de un usuario válido en el sistema. |
| `SystemMode.java` | Enumerador para definir si el sistema está operando en formato "Lectura de Acceso" o "Enrolamiento de Nuevos Usuarios". |
| `SerialPortInfo.java` | Modelo que representa los datos de un puerto serial activo. |
| `CaptureSession.java` | Modelo que administra una "sesión" de captura de datos asociada a un archivo o ventana de tiempo. |
| `AccessLogWriter.java` | Puerto (interfaz) para escribir logs de accesos (implementado por CSV Writer). |
| `AccessLogReader.java` | Puerto (interfaz) para leer logs de accesos guardados. |
| `AccessRepository.java` | Puerto (interfaz) para la persistencia transaccional (base de datos) de los registros. |
| `UserRegistryPort.java` | Puerto (interfaz) para interactuar con la lista de usuarios. |
| `CsvProcessingException.java` | Excepción personalizada lanzada cuando algo falla al procesar, leer o escribir archivos de texto plano. |
| `SerialPortException.java` | Excepción personalizada para manejar fallos de comunicación de las capas bajas con el dispositivo. |

## 5. Capa de Infraestructura (Archivos, Serial y Persistencia)
Implementa las interfaces (puertos) del dominio conectando la app con el mundo exterior: Archivos Locales, Hardware Serial (Arduino) y Base de Datos (MySQL).

### 5.1 Comunicación Serial (Hardware)
| Clase | Descripción |
|---|---|
| `SerialListener.java` | Clase demonio/hilo que escucha continuamente el puerto serial, intercepta los strings enviados por el Arduino y se los pasa a la aplicación. |
| `SerialPortScanner.java` | Explorador y utilitario para identificar qué puertos del PC (COMX/ttyUSB) están disponibles y buscar Arduinos. |

### 5.2 Manejo de Archivos y Resiliencia
| Clase | Descripción |
|---|---|
| `CsvAccessLogWriter.java` | Escribe físicamente en disco los accesos recibidos (`data_logs\access_*.csv`). Implementa el cierre/apertura atómica de archivos para el batch. |
| `CsvAccessLogReader.java` | Lee archivos CSV físicamente del disco y los mapea a objetos para pasarlos a la base de datos durante el batch. |
| `CsvUserRegistryAdapter.java` | Administra un archivo CSV local que simula/almacena a todos los usuarios válidos (`user_registry.csv`). |
| `CsvResilienceService.java` | Demonio en segundo plano (con `@Scheduled`) que audita cada pocos segundos que el CSV activo exista; si alguien lo borra, busca restaurarlo. |
| `BinaryFileTracker.java` | Clase que lee y escribe el archivo binario pequeño e inmutable que rastrea de forma tolerante a fallos qué "CSV" es la sesión actual. |

### 5.3 Persistencia y Base de Datos (JPA/MySQL)
| Clase | Descripción |
|---|---|
| `AccessRepositoryImpl.java` | Clase adaptadora de persistencia que traduce objetos de Dominio a Entidades JPA y los guarda usando el repositorio de Spring Data. |
| `JpaAccessLogRepository.java` | Interfaz de *Spring Data JPA* que realiza consultas mágicas y CRUD en `access_logs` en MySQL. |
| `JpaUserRepository.java` | Interfaz de *Spring Data JPA* para la tabla de usuarios (`users`) en la BD. |
| `AccessLogEntity.java` | Entidad JPA (`@Entity`) que mapea la tabla `access_logs` (columnas: id, udi, timestamp, status...). |
| `UserEntity.java` | Entidad JPA (`@Entity`) que mapea la tabla `users` (id, nfc_uid, user_name...). |
| `StationEntity.java` | Entidad JPA que representa una estación o lector de puerta específica (para un sistema multi-puerta). |
| `DatabaseBackupService.java` | Puente de ejecución del script de MySQL que corre los respaldos usando Stored Procedures nativos. |
