/*
 * Sistema de Control de Acceso IoT - Arduino
 * ===========================================
 * 
 * COMANDOS SERIALES:
 * - 'P': Ping (responde PONG:ACCESS_SYSTEM_V2)
 * - 'W': Esperar tarjeta del admin
 * - 'E': Modo registro (admin validado)
 * - 'A': Modo acceso normal
 * - 'K:nombre': Registro exitoso (muestra nombre)
 * - 'X': Admin rechazado
 * - '1': Acceso concedido
 * - '0': Acceso denegado
 */

#include <Wire.h>
#include <Adafruit_PN532.h>
#include <LiquidCrystal_I2C.h>
#include <Servo.h>

#define PN532_IRQ   2
#define PN532_RESET 3
Adafruit_PN532 nfc(PN532_IRQ, PN532_RESET);
LiquidCrystal_I2C lcd(0x27, 16, 2);
Servo torniquete;

#define SERVO_PIN 9
#define CERRADO 0
#define ABIERTO 90

#define TAG_COOLDOWN_MS 7000

bool modoRegistro = false;
bool esperandoAdmin = false;
bool sesionActiva = false;   // Solo leer tags cuando Java esta conectado
String serialBuffer = "";
unsigned long ultimaLectura = 0;

// =============================================
// Funcion auxiliar: Siempre configurar timeout
// despues de Wire.begin() o nfc.begin()
// porque nfc.begin() llama Wire.begin()
// internamente y RESETEA el timeout.
// =============================================
void setI2CTimeout() {
  Wire.setWireTimeout(3000, true);  // 3ms max, auto-reset bus
}

// =============================================
// Recuperacion del bus I2C + reinicio de todo
// =============================================
void recoverI2CBus() {
  Wire.end();
  
  // 9 pulsos SCL para destrabar SDA
  pinMode(A5, OUTPUT);
  pinMode(A4, INPUT_PULLUP);
  for (int i = 0; i < 9; i++) {
    digitalWrite(A5, LOW);
    delayMicroseconds(10);
    digitalWrite(A5, HIGH);
    delayMicroseconds(10);
  }
  
  // Condicion STOP
  pinMode(A4, OUTPUT);
  digitalWrite(A4, LOW);
  delayMicroseconds(10);
  digitalWrite(A5, HIGH);
  delayMicroseconds(10);
  digitalWrite(A4, HIGH);
  delayMicroseconds(10);
  
  Wire.begin();
  setI2CTimeout();  // CRITICO: re-aplicar timeout
  delay(100);
}

// =============================================
// Reiniciar TODOS los dispositivos I2C
// (LCD + NFC) con timeout protegido
// =============================================
void reiniciarI2C() {
  recoverI2CBus();
  lcd.init();
  lcd.backlight();
  nfc.begin();       // ATENCION: esto llama Wire.begin() y borra el timeout
  setI2CTimeout();   // CRITICO: re-aplicar timeout inmediatamente
  nfc.SAMConfig();
}

void setup() {
  Serial.begin(115200);
  
  Wire.begin();
  setI2CTimeout();
  
  lcd.init();
  lcd.backlight();
  
  // Servo a posicion inicial
  torniquete.attach(SERVO_PIN);
  torniquete.write(CERRADO);
  delay(500);
  torniquete.detach();
  
  nfc.begin();       // Llama Wire.begin() internamente
  setI2CTimeout();   // Re-aplicar timeout
  nfc.SAMConfig();
  
  lcd.clear();
  lcd.print("  IOT  ACCESS");
  lcd.setCursor(0, 1);
  lcd.print("   SYSTEM v2");
  delay(1500);
  mostrarEsperaConexion();
}

void loop() {
  // ESCUCHAR ORDENES DE JAVA
  while (Serial.available() > 0) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      if (serialBuffer.length() > 0) {
        procesarMensaje(serialBuffer);
        serialBuffer = "";
      }
    } else {
      serialBuffer += c;
    }
  }

  // Si no hay sesion activa con Java, no leer tags
  if (!sesionActiva) {
    return;
  }

  // COOLDOWN
  if (millis() - ultimaLectura < TAG_COOLDOWN_MS) {
    return;
  }

  // LEER TARJETA NFC
  uint8_t uid[7];
  uint8_t uidLength;

  if (nfc.readPassiveTargetID(PN532_MIFARE_ISO14443A, uid, &uidLength, 100)) {
    ultimaLectura = millis();
    
    Serial.print("UID:");
    for (uint8_t i = 0; i < uidLength; i++) {
      Serial.print(uid[i], HEX);
      if (i < uidLength - 1) Serial.print("-");
    }
    Serial.println();

    if (esperandoAdmin) {
      lcd.clear();
      lcd.print("VERIFICANDO");
      lcd.setCursor(0, 1);
      lcd.print("ADMIN...");
    } else if (modoRegistro) {
      lcd.clear();
      lcd.print("TAG DETECTADO");
      lcd.setCursor(0, 1);
      lcd.print("PROCESANDO...");
    } else {
      lcd.clear();
      lcd.print("VERIFICANDO...");
    }
    
    delay(500);
  }

  // Auto-recuperacion si I2C tuvo timeout
  if (Wire.getWireTimeoutFlag()) {
    Wire.clearWireTimeoutFlag();
    reiniciarI2C();
    mostrarEspera();
  }
}

void procesarMensaje(String msg) {
  char cmd = msg.charAt(0);
  
  // Cualquier comando de Java activa la sesion
  if (!sesionActiva) {
    sesionActiva = true;
  }

  switch (cmd) {
    case 'P':
      Serial.println("PONG:ACCESS_SYSTEM_V2");
      break;
      
    case 'W':
      esperandoAdmin = true;
      modoRegistro = false;
      lcd.clear();
      lcd.print(">> AUTORIZACION");
      lcd.setCursor(0, 1);
      lcd.print("APROXIME ID ADMIN");
      break;
      
    case 'E':
      modoRegistro = true;
      esperandoAdmin = false;
      lcd.clear();
      lcd.print("ADMIN OK!");
      delay(800);
      lcd.clear();
      lcd.print(">> REGISTRO");
      lcd.setCursor(0, 1);
      lcd.print("APROXIME SU TAG");
      break;
      
    case 'A':
      modoRegistro = false;
      esperandoAdmin = false;
      mostrarEspera();
      break;
      
    case '1':
      abrirTorniquete();
      break;
      
    case '0':
      accesoDenegado();
      break;
      
    case 'K': {
      String nombre = "";
      if (msg.length() > 2 && msg.charAt(1) == ':') {
        nombre = msg.substring(2);
      }
      registroExitoso(nombre);
      break;
    }
      
    case 'X':
      lcd.clear();
      lcd.print(">> DENEGADO");
      lcd.setCursor(0, 1);
      lcd.print("NO ES ADMIN");
      delay(1500);
      esperandoAdmin = false;
      modoRegistro = false;
      mostrarEspera();
      break;
  }
}

void registroExitoso(String nombre) {
  lcd.clear();
  lcd.print("NUEVO USUARIO:");
  lcd.setCursor(0, 1);
  if (nombre.length() > 16) {
    nombre = nombre.substring(0, 16);
  }
  lcd.print(nombre);
  delay(2500);
  
  lcd.clear();
  lcd.print("REGISTRO EXITOSO");
  lcd.setCursor(0, 1);
  lcd.print("BIENVENIDO!");
  delay(1500);
  
  modoRegistro = false;
  esperandoAdmin = false;
  mostrarEspera();
}

void abrirTorniquete() {
  // 1. Mostrar mensaje ANTES del servo
  lcd.clear();
  lcd.print(">> BIENVENIDO");
  lcd.setCursor(0, 1);
  lcd.print("ACCESO PERMITIDO");
  
  // 2. Servo
  torniquete.attach(SERVO_PIN);
  delay(50);
  torniquete.write(ABIERTO);
  delay(3000);
  torniquete.write(CERRADO);
  delay(500);
  torniquete.detach();
  delay(200);
  
  // 3. Recuperar I2C + reiniciar LCD y NFC con timeout protegido
  reiniciarI2C();
  
  // 4. Listo
  mostrarEspera();
}

void accesoDenegado() {
  lcd.clear();
  lcd.print(">> DENEGADO");
  lcd.setCursor(0, 1);
  lcd.print("NO AUTORIZADO");
  delay(1500);
  mostrarEspera();
}

void mostrarEspera() {
  lcd.clear();
  lcd.print("SISTEMA  ACTIVO");
  lcd.setCursor(0, 1);
  lcd.print("APROXIME SU TAG");
}

void mostrarEsperaConexion() {
  lcd.clear();
  lcd.print("   ESPERANDO");
  lcd.setCursor(0, 1);
  lcd.print("  CONEXION PC...");
}
