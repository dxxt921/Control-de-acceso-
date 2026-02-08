# Documentación Técnica - Sistema de Control de Acceso IoT

## 1. Descripción Esquemática de la Arquitectura

### 1.1 Diagrama General del Sistema

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SISTEMA DE CONTROL DE ACCESO IoT                     │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────┐
                              │   SMARTPHONE    │
                              │   (Navegador)   │
                              └────────┬────────┘
                                       │ HTTP/WebSocket
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SERVIDOR SPRING BOOT                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ Presentation│  │ Application │  │   Domain    │  │Infrastructure│        │
│  │    Layer    │◄─┤    Layer    │◄─┤    Layer    │◄─┤    Layer    │        │
│  │             │  │             │  │             │  │             │        │
│  │ Controllers │  │  Services   │  │   Models    │  │ Adapters    │        │
│  │ WebSocket   │  │  DTOs       │  │   Ports     │  │ Serial      │        │
│  └─────────────┘  └─────────────┘  └─────────────┘  └──────┬──────┘        │
└───────────────────────────────────────────────────────────┬────────────────┘
                                                            │
                                                            │ Serial USB
                                                            │ (115200 baud)
                                                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            ARDUINO UNO/MEGA                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │   PN532     │  │   LCD I2C   │  │   SERVO     │  │   Serial    │        │
│  │ NFC Reader  │  │  16x2       │  │  Torniquete │  │   Comm      │        │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Arquitectura de Capas (Clean Architecture)

```
┌────────────────────────────────────────────────────────────────────┐
│                      PRESENTATION LAYER                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │DashboardController│  │ DeviceController │  │AccessWebSocket   │  │
│  │   (Thymeleaf)    │  │   (REST API)     │  │   Handler        │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────┐
│                      APPLICATION LAYER                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │  AccessService   │  │ IdentityService  │  │DeviceManagement  │  │
│  │  (Validación)    │  │ (Usuarios CSV)   │  │   Service        │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  │
│                                                                     │
│  DTOs: AccessRecordDto, RegisteredUserDto, EnrollmentStateDto      │
└────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────┐
│                        DOMAIN LAYER                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │   AccessRecord   │  │  RegisteredUser  │  │   SystemMode     │  │
│  │   AccessStatus   │  │  CaptureSession  │  │ (ACCESO/ENROL)   │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  │
│                                                                     │
│  Ports: UserRegistryPort, AccessLogWriter, AccessRepository        │
└────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────┐
│                    INFRASTRUCTURE LAYER                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │  SerialListener  │  │CsvUserRegistry   │  │ CsvAccessLog     │  │
│  │ (jSerialComm)    │  │   Adapter        │  │   Writer         │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  │
│  ┌──────────────────┐  ┌──────────────────┐                        │
│  │SerialPortScanner │  │JPA Repositories  │                        │
│  └──────────────────┘  └──────────────────┘                        │
└────────────────────────────────────────────────────────────────────┘
```

### 1.3 Flujo de Datos

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ Tarjeta  │───▶│  PN532   │───▶│ Arduino  │───▶│   Java   │───▶│Dashboard │
│   NFC    │    │  Reader  │    │  Serial  │    │ Backend  │    │   Web    │
└──────────┘    └──────────┘    └──────────┘    └──────────┘    └──────────┘
                                      │               │
                                      │◀──────────────┘
                                      │    '1' o '0'
                                      ▼
                               ┌──────────┐
                               │  Servo   │
                               │Torniquete│
                               └──────────┘
```

---

## 2. Código de la Interfaz (Backend Spring Boot)

### 2.1 Estructura del Proyecto

```
iot-access-system/
├── src/main/java/com/iotaccess/
│   ├── application/
│   │   ├── dto/                    # Data Transfer Objects
│   │   │   ├── AccessRecordDto.java
│   │   │   ├── RegisteredUserDto.java
│   │   │   └── EnrollmentStateDto.java
│   │   ├── service/                # Servicios de aplicación
│   │   │   ├── AccessService.java
│   │   │   ├── AccessServiceImpl.java
│   │   │   ├── IdentityService.java
│   │   │   ├── IdentityServiceImpl.java
│   │   │   └── DeviceManagementService.java
│   │   └── scheduler/
│   │       └── BatchProcessingJob.java
│   ├── domain/
│   │   ├── model/                  # Modelos de dominio
│   │   │   ├── AccessRecord.java
│   │   │   ├── AccessStatus.java
│   │   │   ├── RegisteredUser.java
│   │   │   └── SystemMode.java
│   │   └── port/                   # Interfaces (puertos)
│   │       ├── UserRegistryPort.java
│   │       └── AccessLogWriter.java
│   ├── infrastructure/
│   │   ├── file/                   # Adaptadores CSV
│   │   │   ├── CsvUserRegistryAdapter.java
│   │   │   └── CsvAccessLogWriter.java
│   │   └── serial/                 # Comunicación serial
│   │       ├── SerialListener.java
│   │       └── SerialPortScanner.java
│   └── presentation/
│       ├── controller/             # Controladores REST
│       │   ├── DashboardController.java
│       │   └── DeviceController.java
│       └── websocket/
│           └── AccessWebSocketHandler.java
└── src/main/resources/
    ├── templates/
    │   ├── index.html              # Página de inicio
    │   └── dashboard.html          # Panel principal
    └── application.properties
```

### 2.2 Componentes Principales

#### 2.2.1 AccessServiceImpl - Servicio Principal de Acceso

```java
@Service
public class AccessServiceImpl implements AccessService {

    private final SerialListener serialListener;
    private final UserRegistryPort userRegistryPort;
    private final AccessWebSocketHandler webSocketHandler;

    /**
     * Procesa un UID recibido del lector NFC.
     * 1. Verifica si el UID está registrado en user_registry.csv
     * 2. Envía respuesta al Arduino ('1' = permitido, '0' = denegado)
     * 3. Registra el acceso en el log CSV
     * 4. Notifica a clientes WebSocket en tiempo real
     */
    @Override
    public void processIncomingUid(String uid) {
        // Verificar SOLO en el registro local (user_registry.csv)
        boolean isRegistered = userRegistryPort.existsByUid(uid);
        
        String userName = userRegistryPort.findByUid(uid)
                .map(user -> user.getName())
                .orElse("No registrado");

        AccessStatus status = isRegistered ? AccessStatus.GRANTED : AccessStatus.DENIED;

        // Enviar respuesta al Arduino
        char response = isRegistered ? '1' : '0';
        serialListener.sendCommand(response);

        // Crear y guardar registro de acceso
        AccessRecord record = AccessRecord.builder()
                .uid(uid)
                .timestamp(LocalDateTime.now())
                .status(status)
                .userName(userName)
                .build();

        logWriter.write(record);
        
        // Notificar a clientes WebSocket
        webSocketHandler.broadcastRecord(AccessRecordDto.fromDomain(record));
    }
}
```

#### 2.2.2 DeviceManagementService - Gestión de Enrolamiento

```java
@Service
public class DeviceManagementService {

    private static final int ENROLLMENT_TIMEOUT_SECONDS = 20;
    
    private final AtomicReference<SystemMode> currentMode = 
        new AtomicReference<>(SystemMode.ACCESO);

    /**
     * Inicia modo de enrolamiento por 20 segundos.
     * - Envía comando 'E' al Arduino
     * - Inicia countdown visible en el dashboard
     * - Captura el siguiente UID escaneado
     */
    public EnrollmentStateDto startEnrollmentMode() {
        currentMode.set(SystemMode.ENROLAMIENTO);
        sendSerialCommand('E');
        webSocketHandler.broadcastEnrollmentMode(true, 20, null);
        // Programar timeout automático
        scheduler.schedule(() -> cancelEnrollment(), 20, TimeUnit.SECONDS);
        return EnrollmentStateDto.active(20, null);
    }

    /**
     * Confirma el registro de un nuevo dispositivo.
     * - Guarda en user_registry.csv
     * - Envía comando 'K' al Arduino
     * - Vuelve a modo acceso normal
     */
    public RegisteredUserDto confirmEnrollment(String uid, String name) {
        RegisteredUserDto user = identityService.registerUser(uid, name);
        sendSerialCommand('K');
        endEnrollmentMode();
        return user;
    }
}
```

#### 2.2.3 SerialListener - Comunicación Serial

```java
@Component
public class SerialListener {

    // Patrón para detectar UID: "UID:XX-XX-XX-XX"
    private static final Pattern UID_PATTERN = 
        Pattern.compile("UID:?\\s*([A-Fa-f0-9\\s\\-]+)");

    /**
     * Inicia escucha en puerto serial especificado.
     * Cada línea recibida se procesa para detectar UIDs.
     */
    public void start(String portName, Consumer<String> onUidReceived) {
        currentPort = portScanner.findPort(portName);
        currentPort.setBaudRate(115200);
        currentPort.openPort();
        executor.submit(() -> readLoop());
    }

    /**
     * Envía un comando de un carácter al Arduino.
     * Comandos soportados:
     * - 'E': Entrar en modo enrolamiento
     * - 'A': Volver a modo acceso
     * - 'K': Confirmación de registro
     * - '1': Acceso concedido (abrir servo)
     * - '0': Acceso denegado
     */
    public void sendCommand(char command) {
        byte[] data = new byte[] { (byte) command };
        currentPort.writeBytes(data, 1);
    }
}
```

### 2.3 Descripción de Pantallas

#### Pantalla 1: Inicio de Sesión (`index.html`)
- **Propósito**: Configurar y iniciar una nueva sesión de captura
- **Elementos**:
  - Selector de puerto COM disponible
  - Campo para nombre de sesión
  - Botón "Iniciar Sesión"
- **Funcionamiento**: Al iniciar, conecta con el Arduino y abre el dashboard

#### Pantalla 2: Dashboard Principal (`dashboard.html`)
- **Propósito**: Monitoreo en tiempo real y gestión de dispositivos
- **Secciones**:
  1. **Header**: Estado de conexión WebSocket, hora actual
  2. **Estadísticas**: Total accesos, permitidos, denegados
  3. **Gestión de Dispositivos**:
     - Botón "ESCANEAR NUEVO" para enrolamiento
     - Tabla de dispositivos registrados
     - Botón eliminar por dispositivo
  4. **Registro de Accesos**: Tabla con últimos 50 accesos en tiempo real
- **Interacciones**:
  - Los accesos aparecen automáticamente via WebSocket
  - Toast notifications para cada evento
  - Modal de confirmación para enrolamiento y eliminación

---

## 3. Método de Comunicación Arduino ↔ Java

### 3.1 Protocolo Serial

| Aspecto | Valor |
|---------|-------|
| **Interfaz** | USB Serial (COM port) |
| **Velocidad** | 115200 baudios |
| **Bits de datos** | 8 |
| **Bits de parada** | 1 |
| **Paridad** | Ninguna |
| **Control de flujo** | Ninguno |

### 3.2 Formato de Mensajes

#### Arduino → Java (Lectura NFC)
```
UID:XX-XX-XX-XX\n
```
- **Ejemplo**: `UID:EB-EE-C0-1\n`
- El UID se envía en hexadecimal separado por guiones
- Termina con salto de línea (`\n`)

#### Java → Arduino (Comandos)
| Comando | Significado | Acción en Arduino |
|---------|-------------|-------------------|
| `E` | Enter Enrollment | LCD: "MODO REGISTRO" |
| `A` | Access Mode | LCD: "SISTEMA ACTIVO / ACERQUE NFC" |
| `K` | Konfirmation | LCD: "REGISTRADO OK", beep |
| `1` | Granted | LCD: "BIENVENIDO", abrir servo 3s |
| `0` | Denied | LCD: "NO AUTORIZADO", 2s espera |

### 3.3 Diagrama de Secuencia - Flujo de Acceso

```
┌────────┐          ┌────────┐          ┌────────┐          ┌────────┐
│Tarjeta │          │Arduino │          │  Java  │          │Dashboard│
│  NFC   │          │        │          │ Spring │          │  Web   │
└───┬────┘          └───┬────┘          └───┬────┘          └───┬────┘
    │                   │                   │                   │
    │ Acercar tarjeta   │                   │                   │
    │──────────────────▶│                   │                   │
    │                   │                   │                   │
    │                   │ UID:EB-EE-C0-1    │                   │
    │                   │──────────────────▶│                   │
    │                   │                   │                   │
    │                   │                   │ Verificar CSV     │
    │                   │                   │◀─────────────────▶│
    │                   │                   │                   │
    │                   │        '1'        │                   │
    │                   │◀──────────────────│                   │
    │                   │                   │                   │
    │                   │                   │ WebSocket:        │
    │                   │                   │ NEW_RECORD        │
    │                   │                   │──────────────────▶│
    │                   │                   │                   │
    │   BIENVENIDO      │                   │                   │
    │◀──────────────────│                   │                   │
    │   (Servo abre)    │                   │                   │
    │                   │                   │                   │
```

### 3.4 Diagrama de Secuencia - Flujo de Enrolamiento

```
┌────────┐          ┌────────┐          ┌────────┐          ┌────────┐
│Usuario │          │Arduino │          │  Java  │          │Dashboard│
└───┬────┘          └───┬────┘          └───┬────┘          └───┬────┘
    │                   │                   │                   │
    │                   │                   │   Click "ESCANEAR │
    │                   │                   │◀──────────────────│
    │                   │                   │                   │
    │                   │        'E'        │                   │
    │                   │◀──────────────────│                   │
    │                   │                   │                   │
    │   MODO REGISTRO   │                   │ WebSocket:        │
    │◀──────────────────│                   │ ENROLLMENT_MODE   │
    │                   │                   │──────────────────▶│
    │                   │                   │                   │
    │ Acercar tarjeta   │                   │   (Countdown 20s) │
    │──────────────────▶│                   │                   │
    │                   │                   │                   │
    │                   │ UID:5A-92-50-6    │                   │
    │                   │──────────────────▶│                   │
    │                   │                   │                   │
    │                   │                   │ WebSocket:        │
    │                   │                   │ UID_CAPTURED      │
    │                   │                   │──────────────────▶│
    │                   │                   │                   │
    │                   │                   │   (Modal: nombre) │
    │                   │                   │                   │
    │                   │                   │   POST /confirm   │
    │                   │                   │◀──────────────────│
    │                   │                   │                   │
    │                   │        'K'        │                   │
    │                   │◀──────────────────│                   │
    │                   │                   │                   │
    │                   │        'A'        │                   │
    │                   │◀──────────────────│                   │
    │                   │                   │                   │
    │   REGISTRADO OK   │                   │ WebSocket:        │
    │◀──────────────────│                   │ ENROLLMENT_COMPLETE│
    │                   │                   │──────────────────▶│
```

---

## 4. Código del Sistema Embebido (Arduino)

### 4.1 Código Completo

```cpp
#include <Wire.h>
#include <Adafruit_PN532.h>
#include <LiquidCrystal_I2C.h>
#include <Servo.h>

// =============== CONFIGURACIÓN DE PINES ===============
#define PN532_IRQ   2      // Pin de interrupción del PN532
#define PN532_RESET 3      // Pin de reset del PN532
#define SERVO_PIN   9      // Pin del servo motor
#define CERRADO     0      // Posición cerrada del servo (grados)
#define ABIERTO     90     // Posición abierta del servo (grados)

// =============== OBJETOS GLOBALES ===============
Adafruit_PN532 nfc(PN532_IRQ, PN532_RESET);  // Lector NFC
LiquidCrystal_I2C lcd(0x27, 16, 2);           // LCD 16x2 I2C
Servo torniquete;                              // Servo del torniquete

// =============== VARIABLES DE ESTADO ===============
bool modoRegistro = false;  // Flag para modo enrolamiento

// =============== CONFIGURACIÓN INICIAL ===============
void setup() {
  // Inicializar comunicación serial con Java
  Serial.begin(115200);
  
  // Inicializar LCD
  lcd.init();
  lcd.backlight();
  
  // Inicializar servo en posición cerrada
  torniquete.attach(SERVO_PIN);
  torniquete.write(CERRADO);
  
  // Inicializar lector NFC PN532
  nfc.begin();
  nfc.SAMConfig();  // Configurar el módulo SAM
  
  // Mostrar pantalla de espera
  mostrarEspera();
}

// =============== BUCLE PRINCIPAL ===============
void loop() {
  // --- PASO 1: Procesar comandos de Java ---
  if (Serial.available() > 0) {
    char cmd = Serial.read();
    
    switch(cmd) {
      case 'E':  // Entrar en modo enrolamiento
        modoRegistro = true;
        lcd.clear();
        lcd.print("MODO REGISTRO");
        break;
        
      case 'A':  // Volver a modo acceso normal
        modoRegistro = false;
        mostrarEspera();
        break;
        
      case '1':  // Java dice: ACCESO CONCEDIDO
        abrirTorniquete();
        break;
        
      case '0':  // Java dice: ACCESO DENEGADO
        accesoDenegado();
        break;
        
      case 'K':  // Confirmación de registro exitoso
        lcd.clear();
        lcd.print("REGISTRADO OK");
        delay(2000);
        modoRegistro = false;
        mostrarEspera();
        break;
    }
  }

  // --- PASO 2: Leer tarjetas NFC ---
  uint8_t uid[7];       // Buffer para almacenar UID (máx 7 bytes)
  uint8_t uidLength;    // Longitud real del UID

  // Intentar leer una tarjeta Mifare
  if (nfc.readPassiveTargetID(PN532_MIFARE_ISO14443A, uid, &uidLength)) {
    
    // Enviar UID a Java en formato hexadecimal
    Serial.print("UID:");
    for (uint8_t i = 0; i < uidLength; i++) {
      Serial.print(uid[i], HEX);
      if (i < uidLength - 1) Serial.print("-");
    }
    Serial.println();  // Terminar con salto de línea

    // Si estamos en modo acceso normal, mostrar "VALIDANDO"
    if (!modoRegistro) {
      lcd.clear();
      lcd.print("VALIDANDO...");
      // Esperar respuesta de Java ('1' o '0')
    }
    
    // Pausa para evitar múltiples lecturas de la misma tarjeta
    delay(2000);
  }
}

// =============== FUNCIONES AUXILIARES ===============

/**
 * Abre el torniquete temporalmente.
 * Llamada cuando Java envía '1' (acceso concedido).
 */
void abrirTorniquete() {
  lcd.clear();
  lcd.print("BIENVENIDO");
  
  torniquete.write(ABIERTO);  // Abrir
  delay(3000);                 // Mantener abierto 3 segundos
  torniquete.write(CERRADO);  // Cerrar
  
  mostrarEspera();
}

/**
 * Muestra mensaje de acceso denegado.
 * Llamada cuando Java envía '0' (acceso denegado).
 */
void accesoDenegado() {
  lcd.clear();
  lcd.print("NO AUTORIZADO");
  delay(2000);
  mostrarEspera();
}

/**
 * Muestra la pantalla de espera estándar.
 */
void mostrarEspera() {
  lcd.clear();
  lcd.print("SISTEMA ACTIVO");
  lcd.setCursor(0, 1);
  lcd.print("ACERQUE NFC");
}
```

### 4.2 Descripción de Funcionamiento

#### Componentes de Hardware

| Componente | Modelo | Conexión | Función |
|------------|--------|----------|---------|
| Microcontrolador | Arduino UNO/Mega | USB | Procesamiento central |
| Lector NFC | PN532 | I2C/SPI (IRQ: D2, RST: D3) | Lectura de tarjetas Mifare |
| Pantalla | LCD 16x2 I2C | I2C (addr: 0x27) | Feedback visual al usuario |
| Actuador | Servo SG90 | PWM (D9) | Simula torniquete/cerradura |

#### Estados del Sistema

```
┌─────────────────────────────────────────────────────────────┐
│                    DIAGRAMA DE ESTADOS                       │
└─────────────────────────────────────────────────────────────┘

          ┌──────────────────────────────────────────┐
          │                                          │
          ▼                                          │
    ┌───────────┐    Comando 'E'    ┌───────────┐   │
    │  MODO     │ ─────────────────▶│   MODO    │   │
    │  ACCESO   │                   │ REGISTRO  │   │
    │           │◀───────────────── │           │   │
    └─────┬─────┘   Comando 'A'     └─────┬─────┘   │
          │         o 'K'                 │         │
          │                               │         │
   UID    │                        UID    │         │
detectado │                     detectado │         │
          ▼                               ▼         │
    ┌───────────┐                   ┌───────────┐   │
    │VALIDANDO..│                   │  Espera   │   │
    │           │                   │confirmación│   │
    └─────┬─────┘                   └───────────┘   │
          │                                         │
    ┌─────┴─────┐                                   │
    │           │                                   │
    ▼           ▼                                   │
┌───────┐  ┌───────┐                                │
│  '1'  │  │  '0'  │                                │
│ABIERTO│  │CERRADO│                                │
└───┬───┘  └───┬───┘                                │
    │          │                                    │
    └──────────┴────────────────────────────────────┘
                    (Vuelve a MODO ACCESO)
```

#### Flujo de Ejecución

1. **Inicialización** (`setup()`)
   - Configura serial a 115200 baudios
   - Inicializa LCD con backlight
   - Posiciona servo en CERRADO (0°)
   - Configura módulo NFC PN532
   - Muestra "SISTEMA ACTIVO / ACERQUE NFC"

2. **Bucle Principal** (`loop()`)
   - **Fase 1**: Revisa si hay comandos de Java en serial
   - **Fase 2**: Intenta leer tarjeta NFC
   - Si detecta tarjeta: envía UID y espera respuesta

3. **Comandos Soportados**

| Comando | Origen | Acción |
|---------|--------|--------|
| `E` | Java | Entrar modo registro, LCD: "MODO REGISTRO" |
| `A` | Java | Volver modo acceso, LCD: pantalla normal |
| `1` | Java | Abrir servo 3s, LCD: "BIENVENIDO" |
| `0` | Java | Mantener cerrado, LCD: "NO AUTORIZADO" |
| `K` | Java | Confirmación registro, LCD: "REGISTRADO OK" |

### 4.3 Librerías Requeridas

```
Nombre                  Versión    Instalación Arduino IDE
───────────────────────────────────────────────────────────
Adafruit PN532         1.3.0      Sketch > Include Library > Manage Libraries
LiquidCrystal I2C      1.1.2      Sketch > Include Library > Manage Libraries
Servo                  (built-in) Incluida con Arduino IDE
Wire                   (built-in) Incluida con Arduino IDE
```

---

## 5. Almacenamiento de Datos

### 5.1 Archivo de Usuarios (`user_registry.csv`)

```csv
"uid","name","registered_at"
"EB-EE-C0-1","Arturo","2026-02-05 13:55:51"
"5A-92-50-6","María García","2026-02-05 14:30:22"
```

### 5.2 Archivo de Accesos (`[sesion]_[fecha].csv`)

```csv
timestamp,uid,status,station_id
2026-02-05 13:40:59,5A-92-50-6,DENIED,1
2026-02-05 13:41:32,EB-EE-C0-1,GRANTED,1
2026-02-05 13:41:54,8-D8-50-96,DENIED,1
```

---

## 6. Endpoints API REST

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/devices` | Lista todos los dispositivos registrados |
| GET | `/api/devices/count` | Obtiene cantidad de dispositivos |
| GET | `/api/devices/enrollment/status` | Estado actual del enrolamiento |
| POST | `/api/devices/enrollment/start` | Inicia modo enrolamiento (20s) |
| POST | `/api/devices/enrollment/confirm` | Confirma registro con nombre |
| POST | `/api/devices/enrollment/cancel` | Cancela enrolamiento activo |
| DELETE | `/api/devices/{uid}` | Elimina un dispositivo |
| GET | `/api/stats` | Estadísticas del día |
| POST | `/api/session/start` | Inicia sesión de captura |
| POST | `/api/session/stop` | Detiene sesión actual |
