# REPORTE FINAL — Sistema de Control de Acceso IoT

**Materia:** Interconexión de Dispositivos  
**Integrantes del equipo:** [Escribir nombres aquí]  
**Fecha de entrega:** [Escribir fecha aquí]

---

## 1. Descripción del Proyecto, Problemática y Solución Tecnológica

### 1.1 Problemática

En instituciones educativas y centros de trabajo, el control de acceso físico se realiza de forma manual mediante credenciales visuales o listas en papel. Esto genera:

- **Inseguridad**: Personas no autorizadas pueden ingresar sin verificación.
- **Falta de trazabilidad**: No hay registros digitales de quién ingresó y a qué hora.
- **Ineficiencia operativa**: El personal de vigilancia debe detener y verificar a cada individuo manualmente.
- **Ausencia de reportes**: No existen estadísticas de acceso para toma de decisiones.

### 1.2 Solución Tecnológica

Se desarrolló un **Sistema de Control de Acceso IoT** que integra hardware embebido (Arduino + lector NFC PN532) con una interfaz computacional (Spring Boot + Dashboard Web). El sistema:

1. **Lee tarjetas NFC** mediante un sensor PN532 conectado a un Arduino.
2. **Valida el acceso** comparando el UID contra un registro de usuarios autorizados.
3. **Controla un actuador** (servo motor) que simula un torniquete/cerradura electrónica.
4. **Registra cada acceso** en archivos CSV y en base de datos MySQL.
5. **Muestra un dashboard en tiempo real** vía WebSocket para monitoreo de accesos.
6. **Permite el enrolamiento de usuarios** directamente desde la interfaz web con validación de administrador.
7. **Ejecuta procesos batch automáticos** para respaldo diario de datos (CSV→MySQL).

### 1.3 Tecnologías Empleadas

| Componente | Tecnología | Versión |
|------------|------------|---------|
| Backend | Java + Spring Boot | 3.2.2 |
| Base de datos | MySQL | 8.x |
| Comunicación serial | jSerialComm | 2.10.4 |
| Procesamiento CSV | OpenCSV | 5.9 |
| Frontend | Thymeleaf + Tailwind CSS | - |
| Tiempo real | WebSocket (Spring) | - |
| Microcontrolador | Arduino UNO/Mega | - |
| Sensor NFC | PN532 (Adafruit) | - |
| Actuador | Servo Motor SG90 | - |
| Display | LCD 16x2 I2C | - |

---

## 2. Descripción Esquemática de la Arquitectura

### 2.1 Arquitectura General del Sistema

```
┌─────────────────────────────────────────────────────────────────┐
│                 SISTEMA DE CONTROL DE ACCESO IoT                │
└─────────────────────────────────────────────────────────────────┘

                          ┌─────────────────┐
                          │   NAVEGADOR WEB │
                          │   (Dashboard)   │
                          └────────┬────────┘
                                   │ HTTP / WebSocket
                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                     SERVIDOR SPRING BOOT                        │
│                                                                 │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ Presentation│  │  Application │  │    Infrastructure      │ │
│  │   Layer     │──│    Layer     │──│       Layer            │ │
│  │             │  │              │  │                        │ │
│  │ Controllers │  │  Services    │  │ Serial, CSV, JPA, WS  │ │
│  │ WebSocket   │  │  Scheduler   │  │                        │ │
│  └─────────────┘  └──────────────┘  └──────────┬─────────────┘ │
│                                                │               │
│                          ┌──────────┐          │               │
│                          │  Domain  │          │               │
│                          │ Models + │          │               │
│                          │  Ports   │          │               │
│                          └──────────┘          │               │
└────────────────────────────────────────────────┼───────────────┘
                                                 │
                    ┌────────────────────────┐    │ USB Serial
                    │      MySQL 8.x        │    │ 115200 baud
                    │  smart_access_db      │    │
                    └────────────────────────┘    ▼
                                          ┌──────────────┐
                                          │ ARDUINO UNO  │
                                          │              │
                                          │ PN532 + LCD  │
                                          │ + Servo      │
                                          └──────────────┘
```

### 2.2 Arquitectura Hexagonal (Clean Architecture)

El proyecto implementa **Arquitectura Hexagonal** con 4 capas:

```
╔══════════════════════════════════════════════════════════════╗
║                    PRESENTATION LAYER                        ║
║  ConfigController · DashboardController · DeviceController   ║
║  SystemStatusController · AccessWebSocketHandler             ║
╠══════════════════════════════════════════════════════════════╣
║                    APPLICATION LAYER                         ║
║  AccessServiceImpl · IdentityServiceImpl                     ║
║  DeviceManagementService · BatchProcessingJob                ║
║  DTOs: AccessRecordDto, RegisteredUserDto, etc.              ║
╠══════════════════════════════════════════════════════════════╣
║                      DOMAIN LAYER                            ║
║  Models: AccessRecord, RegisteredUser, SystemMode, etc.      ║
║  Ports: UserRegistryPort, AccessLogWriter, AccessRepository  ║
║  Exceptions: CsvProcessingException, SerialPortException     ║
╠══════════════════════════════════════════════════════════════╣
║                   INFRASTRUCTURE LAYER                       ║
║  Serial: SerialListener, SerialPortScanner                   ║
║  File: CsvAccessLogWriter/Reader, CsvUserRegistryAdapter     ║
║       CsvResilienceService, BinaryFileTracker                ║
║  Persistence: AccessRepositoryImpl, DatabaseBackupService    ║
║       JpaAccessLogRepository, JpaUserRepository              ║
╚══════════════════════════════════════════════════════════════╝
```

### 2.3 Flujo de Datos Principal

```
┌────────┐    ┌────────┐    ┌────────┐    ┌────────┐    ┌──────────┐
│Tarjeta │───▶│ PN532  │───▶│Arduino │───▶│ Java   │───▶│Dashboard │
│  NFC   │    │ Reader │    │ Serial │    │Backend │    │   Web    │
└────────┘    └────────┘    └────────┘    └───┬────┘    └──────────┘
                                  ▲          │
                                  │◄─────────┘  '1' o '0'
                                  ▼
                           ┌──────────┐    ┌──────────┐
                           │  Servo   │    │ CSV/MySQL│
                           │Torniquete│    │  Storage │
                           └──────────┘    └──────────┘
```

---

## 3. Diagrama Electrónico del Sistema Embebido

### 3.1 Lista de Componentes

| # | Componente | Modelo | Cantidad |
|---|-----------|--------|----------|
| 1 | Microcontrolador | Arduino UNO R3 | 1 |
| 2 | Lector NFC | PN532 (Adafruit) | 1 |
| 3 | Pantalla LCD | 16x2 con módulo I2C (PCF8574) | 1 |
| 4 | Servo Motor | SG90 (o MG996R) | 1 |
| 5 | Cables Dupont | Macho-Hembra | ~15 |
| 6 | Cable USB | Tipo A-B | 1 |
| 7 | Protoboard | 830 puntos | 1 |

### 3.2 Diagrama de Conexiones

```
                    ┌──────────────────────────┐
                    │      ARDUINO UNO         │
                    │                          │
    PN532 IRQ ──────│ D2          D9 ──────────│──── Servo (Signal)
    PN532 RST ──────│ D3                       │
                    │                          │
    PN532 SDA ──────│ SDA (A4)                 │
    PN532 SCL ──────│ SCL (A5)                 │
    LCD SDA   ──────│ SDA (A4)   5V ───────────│──┬─ PN532 VCC
    LCD SCL   ──────│ SCL (A5)                 │  ├─ LCD VCC
                    │            GND ──────────│──┼─ Servo GND
                    │                          │  ├─ PN532 GND
                    │            USB ──────────│  └─ LCD GND
                    │         (a PC/Java)      │
                    └──────────────────────────┘
                              │
                         Servo VCC ── 5V

Bus I2C compartido:
  - PN532: Dirección 0x24
  - LCD:   Dirección 0x27
```

### 3.3 Configuración de Pines en Código

```cpp
#define PN532_IRQ   2      // Pin de interrupción del PN532
#define PN532_RESET 3      // Pin de reset del PN532
#define SERVO_PIN   9      // Pin PWM del servo motor
#define CERRADO     0      // Posición cerrada (grados)
#define ABIERTO     90     // Posición abierta (grados)
// LCD I2C: dirección 0x27, 16 columnas, 2 filas
```

---

## 4. Evidencia Fotográfica

> **NOTA:** Insertar aquí las fotografías del prototipo armado, las conexiones, la LCD en funcionamiento y las capturas de pantalla del dashboard.

### Fotografías requeridas:
1. Vista general del prototipo armado (Arduino + PN532 + LCD + Servo)
2. Detalle de conexiones I2C (PN532 y LCD al bus compartido)
3. Conexión del servo motor
4. LCD mostrando "SISTEMA ACTIVO / ACERQUE NFC"
5. LCD mostrando "BIENVENIDO" (acceso concedido)
6. LCD mostrando "NO AUTORIZADO" (acceso denegado)
7. LCD mostrando "MODO REGISTRO" (enrolamiento)
8. Captura del Dashboard web en funcionamiento
9. Captura de la página de configuración de puertos

---

## 5. Código Fuente del Sistema Embebido

### 5.1 Archivo: `access_control_with_admin.ino`

```cpp
#include <Wire.h>
#include <Adafruit_PN532.h>
#include <LiquidCrystal_I2C.h>
#include <Servo.h>

// =============== CONFIGURACIÓN DE PINES ===============
#define PN532_IRQ   2
#define PN532_RESET 3
#define SERVO_PIN   9
#define CERRADO     0
#define ABIERTO     90

// =============== OBJETOS GLOBALES ===============
Adafruit_PN532 nfc(PN532_IRQ, PN532_RESET);
LiquidCrystal_I2C lcd(0x27, 16, 2);
Servo torniquete;

// =============== VARIABLES DE ESTADO ===============
bool modoRegistro = false;
bool esperandoAdmin = false;
String inputBuffer = "";

// =============== SETUP ===============
void setup() {
  Serial.begin(115200);
  lcd.init();
  lcd.backlight();
  torniquete.attach(SERVO_PIN);
  torniquete.write(CERRADO);
  nfc.begin();
  uint32_t versiondata = nfc.getFirmwareVersion();
  if (!versiondata) {
    lcd.clear();
    lcd.print("PN532 NO FOUND");
    while (1);
  }
  nfc.SAMConfig();
  mostrarEspera();
}

// =============== LOOP PRINCIPAL ===============
void loop() {
  // Procesar comandos de Java
  while (Serial.available() > 0) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      procesarComando(inputBuffer);
      inputBuffer = "";
    } else {
      inputBuffer += c;
    }
  }

  // Leer tarjetas NFC
  uint8_t uid[7];
  uint8_t uidLength;

  if (nfc.readPassiveTargetID(PN532_MIFARE_ISO14443A, uid, &uidLength, 100)) {
    // Construir string del UID
    String uidStr = "UID:";
    for (uint8_t i = 0; i < uidLength; i++) {
      if (uid[i] < 0x10) uidStr += "0";
      uidStr += String(uid[i], HEX);
      if (i < uidLength - 1) uidStr += "-";
    }
    uidStr.toUpperCase();

    // Enviar a Java
    Serial.println(uidStr);

    if (!modoRegistro && !esperandoAdmin) {
      lcd.clear();
      lcd.print("VALIDANDO...");
    }
    delay(1500);
  }
}

// =============== PROCESAR COMANDOS ===============
void procesarComando(String cmd) {
  if (cmd.length() == 0) return;
  char firstChar = cmd.charAt(0);

  switch (firstChar) {
    case 'P':  // PING
      Serial.println("PONG:ACCESS_SYSTEM");
      break;
    case 'W':  // Esperar admin
      esperandoAdmin = true;
      modoRegistro = false;
      lcd.clear();
      lcd.print("ACERQUE TARJETA");
      lcd.setCursor(0, 1);
      lcd.print("ADMIN");
      break;
    case 'X':  // Admin rechazado
      esperandoAdmin = false;
      lcd.clear();
      lcd.print("ADMIN NO VALIDO");
      delay(2000);
      mostrarEspera();
      break;
    case 'E':  // Modo enrolamiento
      modoRegistro = true;
      esperandoAdmin = false;
      lcd.clear();
      lcd.print("MODO REGISTRO");
      lcd.setCursor(0, 1);
      lcd.print("ACERQUE TARJETA");
      break;
    case 'A':  // Modo acceso
      modoRegistro = false;
      esperandoAdmin = false;
      mostrarEspera();
      break;
    case '1':  // Acceso concedido
      abrirTorniquete();
      break;
    case '0':  // Acceso denegado
      accesoDenegado();
      break;
    case 'K':  // Registro confirmado (K:nombre)
      lcd.clear();
      lcd.print("REGISTRADO!");
      if (cmd.length() > 2) {
        lcd.setCursor(0, 1);
        lcd.print(cmd.substring(2, min((int)cmd.length(), 18)));
      }
      delay(3000);
      modoRegistro = false;
      mostrarEspera();
      break;
  }
}

// =============== FUNCIONES DE ACTUADOR ===============
void abrirTorniquete() {
  lcd.clear();
  lcd.print("BIENVENIDO");
  torniquete.write(ABIERTO);
  delay(3000);
  torniquete.write(CERRADO);
  mostrarEspera();
}

void accesoDenegado() {
  lcd.clear();
  lcd.print("NO AUTORIZADO");
  delay(2000);
  mostrarEspera();
}

void mostrarEspera() {
  lcd.clear();
  lcd.print("SISTEMA ACTIVO");
  lcd.setCursor(0, 1);
  lcd.print("ACERQUE NFC");
}
```

### 5.2 Librerías Arduino Requeridas

| Librería | Versión | Función |
|----------|---------|---------|
| Adafruit PN532 | 1.3.0 | Comunicación con el lector NFC |
| LiquidCrystal I2C | 1.1.2 | Control del LCD vía I2C |
| Servo | (built-in) | Control del servo motor |
| Wire | (built-in) | Protocolo I2C |

---

## 6. Descripción de la Interfaz Computacional

### 6.1 Pantalla de Configuración (`index.html`)

**Propósito:** Configurar la conexión serial e iniciar una sesión de captura de datos.

**Secciones:**
1. **Paso 1 — Selección de Puerto Serial:** Lista todos los puertos COM disponibles con estado (Disponible/En uso). Incluye botón "Probar" que envía PING al Arduino para verificar firmware compatible. Botón "Actualizar" para reescanear puertos.
2. **Paso 2 — Nombre de Sesión:** Campo de texto para nombrar el archivo CSV de la sesión (ej: `registro_acceso_feb3`).
3. **Paso 3 — Iniciar Monitoreo:** Botón que conecta al Arduino y redirige al Dashboard.

**Controles:**
- Búsqueda de puertos por nombre/descripción
- Verificación de firmware vía PING/PONG
- Validación de campos antes de activar el botón de inicio

### 6.2 Dashboard Principal (`dashboard.html`)

**Propósito:** Monitoreo en tiempo real de accesos y gestión de dispositivos.

**Secciones:**

1. **Header:** Logo, estado de conexión (puerto COM), indicador WebSocket "En vivo", hora actual, botones de Configuración/Reconectar/Detener.

2. **Tarjetas de Estadísticas:** 4 cards con: Total Accesos, Permitidos (verde), Denegados (rojo), Último Acceso.

3. **Panel de Estado del Sistema:** Serial Listener (activo/inactivo), Proceso Batch (horario programable), Usuarios registrados, Clientes WebSocket conectados.

4. **Barra de Sesión:** Nombre de sesión actual, hora de inicio, conteo de registros, botones de Test/Ejecutar Batch.

5. **Gestión de Dispositivos:** Botón "ESCANEAR NUEVO" para enrolamiento con validación de admin en 2 fases (primero admin, luego nuevo tag), tabla de dispositivos registrados con acciones de eliminación.

6. **Registro de Accesos en Tiempo Real:** Tabla con últimos 50 registros (Hora, UID, Usuario, Estado) actualizada automáticamente vía WebSocket.

**Modales:**
- Confirmación de detener sesión
- Confirmación de enrolamiento (ingreso de nombre)
- Confirmación de eliminación de dispositivo
- Reconexión de Arduino (selección de nuevo puerto)

### 6.3 Configuración de Puerto y Archivos

- **Puerto Serial:** Configurable vía interfaz web; parámetros: 115200 baud, 8 bits datos, 1 stop bit, sin paridad.
- **Archivos CSV:** Se almacenan en `./data_logs/`, backups en `./data_logs_backup/`, procesados en `./history/`.
- **Archivo Binario:** `.file_tracker.dat` almacena el nombre del CSV activo para recuperación ante fallos.
- **Proceso Batch:** Hora reprogramable desde el dashboard (requiere UID del administrador).

---

## 7. Diagramas de Flujo / UML de la Aplicación

### 7.1 Diagrama de Secuencia — Flujo de Acceso Normal

```
┌────────┐       ┌────────┐       ┌──────────────┐       ┌──────────┐
│Tarjeta │       │Arduino │       │   Java       │       │Dashboard │
│  NFC   │       │        │       │ Spring Boot  │       │   Web    │
└───┬────┘       └───┬────┘       └──────┬───────┘       └────┬─────┘
    │                │                    │                    │
    │ Acercar tag    │                    │                    │
    │───────────────▶│                    │                    │
    │                │  UID:EB-EE-C0-1    │                    │
    │                │───────────────────▶│                    │
    │                │                    │                    │
    │                │                    │ ¿UID registrado?   │
    │                │                    │ userRegistryPort   │
    │                │                    │ .existsByUid()     │
    │                │                    │                    │
    │                │     '1' (Sí)       │                    │
    │                │◀───────────────────│                    │
    │                │                    │                    │
    │                │                    │ csvLogWriter       │
    │                │                    │ .write(record)     │
    │                │                    │                    │
    │                │                    │ WebSocket:         │
    │                │                    │ NEW_RECORD         │
    │                │                    │───────────────────▶│
    │  LCD:          │                    │                    │
    │  BIENVENIDO    │                    │                    │
    │◀───────────────│                    │                    │
    │  Servo ABRE 3s │                    │                    │
```

### 7.2 Diagrama de Secuencia — Enrolamiento con Validación Admin

```
┌────────┐       ┌────────┐       ┌──────────────┐       ┌──────────┐
│Usuario │       │Arduino │       │ Java Backend │       │Dashboard │
└───┬────┘       └───┬────┘       └──────┬───────┘       └────┬─────┘
    │                │                    │                    │
    │                │                    │  Click ESCANEAR    │
    │                │                    │◀───────────────────│
    │                │                    │                    │
    │                │      'W'           │  ADMIN_REQUIRED    │
    │                │◀───────────────────│───────────────────▶│
    │                │                    │                    │
    │  LCD: ACERQUE  │                    │   (Countdown 15s)  │
    │  TARJETA ADMIN │                    │                    │
    │                │                    │                    │
    │ Admin acerca   │                    │                    │
    │ su tarjeta     │                    │                    │
    │───────────────▶│  UID:ADMIN_UID     │                    │
    │                │───────────────────▶│                    │
    │                │                    │ ¿Es admin UID?     │
    │                │      'E'           │  ADMIN_APPROVED    │
    │                │◀───────────────────│───────────────────▶│
    │                │                    │                    │
    │  LCD: MODO     │                    │  ENROLLMENT_MODE   │
    │  REGISTRO      │                    │───────────────────▶│
    │                │                    │   (Countdown 20s)  │
    │                │                    │                    │
    │ Nuevo tag      │                    │                    │
    │───────────────▶│  UID:5A-92-50-6    │                    │
    │                │───────────────────▶│                    │
    │                │                    │  UID_CAPTURED      │
    │                │                    │───────────────────▶│
    │                │                    │                    │
    │                │                    │  POST /confirm     │
    │                │                    │  {uid, nombre}     │
    │                │                    │◀───────────────────│
    │                │                    │                    │
    │                │                    │ save(CSV + MySQL)  │
    │                │     'K:nombre'     │                    │
    │                │◀───────────────────│  ENROLLMENT_       │
    │                │     'A'            │  COMPLETE          │
    │                │◀───────────────────│───────────────────▶│
    │  LCD:          │                    │                    │
    │  REGISTRADO!   │                    │                    │
    │◀───────────────│                    │                    │
```

### 7.3 Diagrama de Flujo — Proceso Batch Diario

```
       ┌──────────────────┐
       │ Trigger: Cron    │
       │ (22:00 diario)   │
       └────────┬─────────┘
                │
                ▼
    ┌───────────────────────┐
    │ ¿Horario operativo?   │──── NO ───▶ FIN
    │ (8:00 - 22:00)        │
    └───────────┬───────────┘
                │ SÍ
                ▼
    ┌───────────────────────┐
    │ 1. Sincronizar        │
    │    user_registry.csv  │
    │    → MySQL (users)    │
    └───────────┬───────────┘
                │
                ▼
    ┌───────────────────────┐
    │ 2. Buscar CSVs        │
    │    pendientes en      │
    │    ./data_logs/       │
    │    (excepto activo)   │
    └───────────┬───────────┘
                │
                ▼
    ┌───────────────────────┐
    │ 3. Leer cada CSV      │
    │    → parsear records  │
    │    → INSERT MySQL     │
    │    (access_logs)      │
    └───────────┬───────────┘
                │
                ▼
    ┌───────────────────────┐
    │ 4. Mover CSVs a       │
    │    ./history/         │
    └───────────┬───────────┘
                │
                ▼
    ┌───────────────────────┐
    │ 5. Ejecutar SP        │
    │    sp_backup_          │
    │    access_logs()      │
    └───────────┬───────────┘
                │
                ▼
    ┌───────────────────────┐
    │ 6. WebSocket:         │
    │    BATCH_COMPLETED    │
    └───────────────────────┘
```

### 7.4 Diagrama de Estados del Sistema

```
                    ┌─────────────┐
                    │   INICIO    │
                    └──────┬──────┘
                           │ startSession()
                           ▼
              ┌────────────────────────┐
              │    MODO ACCESO         │◀──────────────────┐
              │    (SystemMode.ACCESS) │                   │
              └──────────┬─────────────┘                   │
                         │                                 │
            startEnrollmentMode()                          │
                         │                                 │
                         ▼                                 │
              ┌────────────────────────┐                   │
              │   ESPERANDO ADMIN      │   timeout/cancel  │
              │ (WAITING_ADMIN)        │──────────────────▶│
              └──────────┬─────────────┘                   │
                         │                                 │
              admin UID validado                           │
                         │                                 │
                         ▼                                 │
              ┌────────────────────────┐                   │
              │   MODO ENROLAMIENTO    │   timeout/cancel  │
              │   (ENROLLMENT)         │──────────────────▶│
              └──────────┬─────────────┘                   │
                         │                                 │
              confirmEnrollment()                          │
                         │                                 │
                         └─────────────────────────────────┘
```

---

## 8. Código Fuente de la Interfaz Computacional

### 8.1 Estructura Completa del Proyecto

```
iot-access-system/
├── pom.xml                          # Configuración Maven (dependencias)
├── arduino/
│   └── access_control_with_admin.ino  # Sketch del sistema embebido
├── src/main/java/com/iotaccess/
│   ├── IoTAccessApplication.java      # Punto de entrada Spring Boot
│   │
│   ├── application/
│   │   ├── dto/
│   │   │   ├── AccessRecordDto.java       # DTO de registro de acceso
│   │   │   ├── EnrollmentStateDto.java    # DTO de estado de enrolamiento
│   │   │   ├── RegisteredUserDto.java     # DTO de usuario registrado
│   │   │   └── SessionStatusDto.java     # DTO de estado de sesión
│   │   ├── scheduler/
│   │   │   └── BatchProcessingJob.java    # Job batch programable
│   │   └── service/
│   │       ├── AccessService.java         # Interfaz servicio principal
│   │       ├── AccessServiceImpl.java     # Implementación (326 líneas)
│   │       ├── IdentityService.java       # Interfaz gestión usuarios
│   │       ├── IdentityServiceImpl.java   # Implementación (122 líneas)
│   │       └── DeviceManagementService.java # Modos y enrolamiento (371 líneas)
│   │
│   ├── domain/
│   │   ├── exception/
│   │   │   ├── CsvProcessingException.java  # Excepciones de CSV
│   │   │   └── SerialPortException.java     # Excepciones de serial
│   │   ├── model/
│   │   │   ├── AccessRecord.java          # Modelo: registro de acceso
│   │   │   ├── AccessStatus.java          # Enum: GRANTED/DENIED/UNKNOWN
│   │   │   ├── CaptureSession.java        # Modelo: sesión de captura
│   │   │   ├── RegisteredUser.java        # Modelo: usuario registrado
│   │   │   ├── SerialPortInfo.java        # Modelo: info puerto serial
│   │   │   └── SystemMode.java            # Enum: ACCESS/WAITING_ADMIN/ENROLLMENT
│   │   └── port/
│   │       ├── AccessLogReader.java       # Puerto: lectura de logs CSV
│   │       ├── AccessLogWriter.java       # Puerto: escritura de logs CSV
│   │       ├── AccessRepository.java      # Puerto: repositorio MySQL
│   │       └── UserRegistryPort.java      # Puerto: registro de usuarios
│   │
│   ├── infrastructure/
│   │   ├── file/
│   │   │   ├── BinaryFileTracker.java     # Tracker binario (.dat)
│   │   │   ├── CsvAccessLogReader.java    # Lectura CSV para batch
│   │   │   ├── CsvAccessLogWriter.java    # Escritura CSV con backup
│   │   │   ├── CsvResilienceService.java  # Verificación periódica (30s)
│   │   │   └── CsvUserRegistryAdapter.java # Adaptador user_registry.csv
│   │   ├── persistence/
│   │   │   ├── AccessRepositoryImpl.java  # Implementación JPA
│   │   │   ├── DatabaseBackupService.java # Stored procedures
│   │   │   ├── JpaAccessLogRepository.java # Repo access_logs
│   │   │   ├── JpaUserRepository.java     # Repo users
│   │   │   └── entity/
│   │   │       ├── AccessLogEntity.java   # Entidad access_logs
│   │   │       ├── StationEntity.java     # Entidad stations
│   │   │       └── UserEntity.java        # Entidad users
│   │   └── serial/
│   │       ├── SerialListener.java        # Escucha serial USB
│   │       └── SerialPortScanner.java     # Escaneo de puertos COM
│   │
│   └── presentation/
│       ├── config/
│       │   └── WebSocketConfig.java       # Configuración WebSocket
│       ├── controller/
│       │   ├── ConfigController.java      # Configuración + API sesiones
│       │   ├── DashboardController.java   # Dashboard + API records/stats
│       │   ├── DeviceController.java      # API REST dispositivos
│       │   └── SystemStatusController.java # API estado del sistema
│       └── websocket/
│           └── AccessWebSocketHandler.java # Handler WS tiempo real
│
├── src/main/resources/
│   ├── application.properties             # Configuración general
│   ├── schema-entregable3.sql             # Schema para Spring init
│   ├── schema-entregable3-manual.sql      # Schema para ejecución manual
│   └── templates/
│       ├── index.html                     # Página de configuración
│       └── dashboard.html                 # Dashboard principal
```

### 8.2 Dependencias (pom.xml)

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>spring-boot-starter-web</dependency>
    <dependency>spring-boot-starter-data-jpa</dependency>
    <dependency>spring-boot-starter-thymeleaf</dependency>
    <dependency>spring-boot-starter-websocket</dependency>

    <!-- Base de datos -->
    <dependency>mysql-connector-j</dependency>

    <!-- Comunicación Serial -->
    <dependency>com.fazecast:jSerialComm:2.10.4</dependency>

    <!-- Procesamiento CSV -->
    <dependency>com.opencsv:opencsv:5.9</dependency>

    <!-- Utilidades -->
    <dependency>org.projectlombok:lombok</dependency>
</dependencies>
```

### 8.3 Configuración Principal (`application.properties`)

```properties
# Servidor
server.port=8080

# Base de datos MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/smart_access_db
spring.datasource.username=root
spring.jpa.hibernate.ddl-auto=update

# Batch Processing
batch.cron=0 0 22 * * ?        # Ejecutar a las 22:00
operation.start-hour=8
operation.end-hour=22

# Serial
serial.baud-rate=115200

# Archivos
csv.data-logs-path=./data_logs
csv.backup-data-logs-path=./data_logs_backup
csv.history-path=./history

# Administrador
admin.uid=EB-EE-C0-1
```

### 8.4 Endpoints API REST

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/ports` | Lista puertos seriales disponibles |
| POST | `/api/ports/refresh` | Refresca lista de puertos |
| POST | `/api/ports/test` | Prueba PING/PONG en un puerto |
| POST | `/api/session/start` | Inicia sesión de captura |
| POST | `/api/session/stop` | Detiene sesión actual |
| POST | `/api/session/reconnect` | Reconecta Arduino al CSV activo |
| POST | `/api/session/rename` | Renombra la sesión/CSV |
| GET | `/api/session/status` | Estado de la sesión actual |
| GET | `/api/records/latest` | Últimos N registros de acceso |
| GET | `/api/records/today` | Registros de hoy |
| GET | `/api/stats` | Estadísticas del día |
| POST | `/api/batch/run` | Ejecutar batch manualmente |
| POST | `/api/test/access` | Simular un acceso (testing) |
| GET | `/api/devices` | Lista dispositivos registrados |
| GET | `/api/devices/count` | Conteo de dispositivos |
| GET | `/api/devices/enrollment/status` | Estado del enrolamiento |
| POST | `/api/devices/enrollment/start` | Inicia enrolamiento |
| POST | `/api/devices/enrollment/confirm` | Confirma enrolamiento |
| POST | `/api/devices/enrollment/cancel` | Cancela enrolamiento |
| DELETE | `/api/devices/{uid}` | Elimina un dispositivo |
| GET | `/api/system/status` | Estado completo del sistema (JSON) |
| POST | `/api/system/batch/schedule` | Reprogramar hora del batch |

### 8.5 Protocolo WebSocket

**Endpoint:** `ws://localhost:8080/ws/access`

| Tipo de Mensaje | Dirección | Descripción |
|-----------------|-----------|-------------|
| `NEW_RECORD` | Server→Client | Nuevo registro de acceso |
| `SESSION_STATUS` | Server→Client | Cambio de estado de sesión |
| `ENROLLMENT_MODE` | Server→Client | Modo enrolamiento activo/inactivo |
| `UID_CAPTURED` | Server→Client | UID capturado durante enrolamiento |
| `ENROLLMENT_COMPLETE` | Server→Client | Enrolamiento completado |
| `ENROLLMENT_ERROR` | Server→Client | Error en enrolamiento |
| `USER_DELETED` | Server→Client | Usuario eliminado |
| `ADMIN_REQUIRED` | Server→Client | Se requiere tarjeta admin |
| `ADMIN_APPROVED` | Server→Client | Admin validado |
| `ADMIN_REJECTED` | Server→Client | Admin rechazado |
| `BATCH_STARTED` | Server→Client | Batch iniciado |
| `BATCH_COMPLETED` | Server→Client | Batch finalizado |

---

## 9. Script SQL de la Base de Datos

### 9.1 Creación de Base de Datos y Tablas Principales

```sql
-- Crear base de datos
CREATE DATABASE IF NOT EXISTS smart_access_db;
USE smart_access_db;

-- Tabla de estaciones de acceso
CREATE TABLE IF NOT EXISTS stations (
    id INT PRIMARY KEY,
    location_name VARCHAR(100) NOT NULL
);

-- Tabla de usuarios registrados
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nfc_uid VARCHAR(50) UNIQUE NOT NULL,
    user_name VARCHAR(100) NOT NULL,
    role VARCHAR(50) DEFAULT 'usuario',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tabla de registros de acceso
CREATE TABLE IF NOT EXISTS access_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uid_detected VARCHAR(50) NOT NULL,
    access_timestamp DATETIME NOT NULL,
    access_granted BOOLEAN NOT NULL,
    station_id INT,
    FOREIGN KEY (station_id) REFERENCES stations(id)
);

-- Insertar estación por defecto
INSERT IGNORE INTO stations (id, location_name)
VALUES (1, 'Entrada Principal');
```

### 9.2 Tabla de Auditoría

```sql
CREATE TABLE IF NOT EXISTS access_audit_log (
    audit_id INT AUTO_INCREMENT PRIMARY KEY,
    log_id INT NOT NULL,
    uid_detected VARCHAR(50),
    access_timestamp DATETIME,
    access_granted BOOLEAN,
    station_id INT,
    audit_action VARCHAR(10) DEFAULT 'INSERT',
    audit_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 9.3 Tabla de Respaldo

```sql
CREATE TABLE IF NOT EXISTS access_logs_backup (
    backup_id INT AUTO_INCREMENT PRIMARY KEY,
    original_id INT NOT NULL,
    uid_detected VARCHAR(50),
    access_timestamp DATETIME,
    access_granted BOOLEAN,
    station_id INT,
    backup_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 9.4 Stored Procedures

```sql
-- SP: Registrar auditoría
DELIMITER ;;
CREATE PROCEDURE sp_registrar_auditoria(
    IN p_log_id INT, IN p_uid VARCHAR(50),
    IN p_timestamp DATETIME, IN p_granted BOOLEAN,
    IN p_station INT, IN p_action VARCHAR(10)
)
BEGIN
    INSERT INTO access_audit_log
        (log_id, uid_detected, access_timestamp,
         access_granted, station_id, audit_action)
    VALUES
        (p_log_id, p_uid, p_timestamp,
         p_granted, p_station, p_action);
END;;

-- SP: Backup de access_logs
CREATE PROCEDURE sp_backup_access_logs()
BEGIN
    DECLARE registros_respaldados INT DEFAULT 0;
    INSERT INTO access_logs_backup
        (original_id, uid_detected, access_timestamp,
         access_granted, station_id)
    SELECT id, uid_detected, access_timestamp,
           access_granted, station_id
    FROM access_logs
    WHERE id NOT IN (SELECT original_id FROM access_logs_backup);
    SET registros_respaldados = ROW_COUNT();
    SELECT registros_respaldados;
END;;

-- SP: Estadísticas diarias
CREATE PROCEDURE sp_daily_stats()
BEGIN
    SELECT
        COUNT(*) AS total_accesos,
        SUM(CASE WHEN access_granted = 1 THEN 1 ELSE 0 END) AS accesos_concedidos,
        SUM(CASE WHEN access_granted = 0 THEN 1 ELSE 0 END) AS accesos_denegados,
        COUNT(DISTINCT uid_detected) AS usuarios_unicos
    FROM access_logs
    WHERE DATE(access_timestamp) = CURDATE();
END;;
DELIMITER ;
```

### 9.5 Trigger de Auditoría Automática

```sql
DELIMITER ;;
CREATE TRIGGER trg_after_access_insert
AFTER INSERT ON access_logs
FOR EACH ROW
BEGIN
    CALL sp_registrar_auditoria(
        NEW.id, NEW.uid_detected, NEW.access_timestamp,
        NEW.access_granted, NEW.station_id, 'INSERT'
    );
END;;
DELIMITER ;
```

### 9.6 Evento Programado de Backup

```sql
SET GLOBAL event_scheduler = ON;

CREATE EVENT IF NOT EXISTS evt_daily_backup
ON SCHEDULE EVERY 1 DAY
STARTS CONCAT(CURDATE(), ' 23:00:00')
DO
    CALL sp_backup_access_logs();
```

---

## 10. Bitácora de Construcción Semanal

### Semana 1: Planificación y Diseño

| Actividad | Descripción |
|-----------|-------------|
| Definición de problemática | Se identificó la necesidad de un sistema de control de acceso automatizado |
| Selección de tecnologías | Se eligió Arduino + PN532 para hardware y Spring Boot para software |
| Diseño de arquitectura | Se definió la arquitectura hexagonal con 4 capas |
| Adquisición de componentes | Se compraron Arduino UNO, PN532, LCD I2C, servo SG90 |

### Semana 2: Sistema Embebido (Hardware)

| Actividad | Descripción |
|-----------|-------------|
| Montaje del prototipo | Se armó el circuito en protoboard: Arduino + PN532 + LCD + Servo |
| Programación Arduino | Se desarrolló el sketch básico de lectura NFC y control de servo |
| Pruebas de comunicación I2C | Se verificó la comunicación con LCD (0x27) y PN532 (0x24) |
| Protocolo serial | Se definió el protocolo de comunicación UID:XX-XX-XX-XX |

### Semana 3: Backend Spring Boot (Entregable 1)

| Actividad | Descripción |
|-----------|-------------|
| Creación del proyecto | Se inicializó el proyecto Maven con Spring Boot 3.2.2 |
| Dominio y modelos | Se crearon AccessRecord, RegisteredUser, SystemMode y sus ports |
| Comunicación serial | Se implementó SerialListener y SerialPortScanner con jSerialComm |
| Escritura CSV | Se implementó CsvAccessLogWriter para registrar accesos en archivos CSV |
| Interfaz web básica | Se creó index.html para configuración de puerto y sesión |

### Semana 4: Dashboard y WebSocket (Entregable 1)

| Actividad | Descripción |
|-----------|-------------|
| Dashboard en tiempo real | Se desarrolló dashboard.html con Thymeleaf y Tailwind CSS |
| WebSocket | Se implementó AccessWebSocketHandler para actualizaciones en tiempo real |
| AccessServiceImpl | Se desarrolló el servicio principal con procesamiento de UIDs |
| Pruebas de integración | Se probó el flujo completo: NFC → Arduino → Java → Dashboard |

### Semana 5: Gestión de Dispositivos (Entregable 2)

| Actividad | Descripción |
|-----------|-------------|
| Enrolamiento con validación admin | Se implementó DeviceManagementService con 3 modos del sistema |
| Protocolo admin | Se agregaron comandos 'W', 'X', 'E', 'K' al Arduino |
| Registro de usuarios | Se implementó CsvUserRegistryAdapter para user_registry.csv |
| CRUD dispositivos | Se creó DeviceController con endpoints REST para gestión completa |
| Modales de UI | Se agregaron modales de enrolamiento, eliminación y reconexión |

### Semana 6: Base de Datos MySQL (Entregable 3)

| Actividad | Descripción |
|-----------|-------------|
| Schema MySQL | Se diseñaron tablas: access_logs, users, stations, audit, backup |
| JPA/Hibernate | Se crearon entidades y repositorios JPA |
| Stored Procedures | Se implementaron sp_backup_access_logs, sp_daily_stats, sp_registrar_auditoria |
| Trigger de auditoría | Se creó trg_after_access_insert para auditoría automática |
| Batch Processing | Se implementó BatchProcessingJob para CSV→MySQL programable |

### Semana 7: Resiliencia y Refinamiento (Entregable 3)

| Actividad | Descripción |
|-----------|-------------|
| Sistema de backups CSV | Se implementó escritura simultánea a CSV principal y backup |
| CsvResilienceService | Verificación periódica cada 30s con restauración automática |
| BinaryFileTracker | Archivo .dat para persistir nombre del CSV activo |
| CsvAccessLogReader | Lectura de CSVs pendientes con fallback a backup |
| Reconexión de Arduino | Se agregó funcionalidad de reconexión sin perder el CSV activo |

### Semana 8: Pruebas Finales y Documentación

| Actividad | Descripción |
|-----------|-------------|
| Pruebas end-to-end | Se probó el flujo completo incluyendo batch, backup y resiliencia |
| Depuración | Se corrigieron bugs de escritura CSV y sincronización de datos |
| SystemStatusController | Se creó endpoint /api/system/status para monitoreo completo |
| Documentación técnica | Se redactó DOCUMENTACION_TECNICA.md con diagramas y especificaciones |
| Reporte final | Se elaboró el presente documento con todos los apartados requeridos |

---

## Anexo A: Ejecución del Sistema

### Requisitos Previos
1. Java JDK 17 o superior
2. MySQL 8.x con base de datos `smart_access_db` creada
3. Arduino IDE (para cargar el sketch al Arduino)
4. Maven 3.8+

### Pasos para Ejecutar
```bash
# 1. Clonar/copiar el proyecto
# 2. Configurar application.properties (credenciales MySQL)
# 3. Cargar sketch en Arduino vía Arduino IDE
# 4. Ejecutar el proyecto:
mvn spring-boot:run
# 5. Abrir navegador en http://localhost:8080
# 6. Seleccionar puerto COM y nombre de sesión
# 7. Click "Iniciar Captura de Datos"
```
