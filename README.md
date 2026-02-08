# Sistema de Control de Acceso IoT - Spring Boot + Arduino

> **Desarrollado por:** Leonardo Fiesco ([@dxxt921](https://github.com/dxxt921))  
> **Derechos:** Todos los derechos reservados © 2026. Prohibida su distribución sin autorización del autor.

## 📋 Descripción

Este proyecto es un prototipo funcional de un sistema de control de acceso IoT que integra una aplicación backend potente en **Spring Boot** con un dispositivo embebido **Arduino**. 

El sistema permite gestionar el acceso físico mediante tarjetas NFC, ofreciendo una interfaz web moderna para monitoreo en tiempo real, gestión de usuarios inalámbrica (enrolamiento industrial) y registros detallados.

## 🚀 Características Principales

- **Dashboard Web en Tiempo Real**: Visualización inmediata de accesos y estado del sistema.
- **Enrolamiento Industrial**: Registro de nuevas tarjetas NFC directamente desde el lector, controlado via web.
- **Comunicación Serial Bidireccional**: Protocolo robusto entre Java y Arduino para validación y control de hardware (torniquete/servo).
- **Persistencia Híbrida**: Sincronización inteligente entre registros locales (CSV) de alta velocidad y base de datos MySQL.
- **Arquitectura Limpia**: Diseño modular siguiendo principios de Clean Architecture y puertos/adaptadores.

## 🛠️ Tecnologías

- **Backend**: Java 17, Spring Boot 3.x, Spring WebSocket
- **Hardware**: Arduino (Uno/Mega), Lector NFC PN532, Servo SG90, LCD I2C
- **Frontend**: HTML5, Tailwind CSS, JavaScript (Vanilla)
- **Persistencia**: CSV (OpenCSV), JPA/Hibernate, MySQL

## 📦 Estructura del Proyecto

El código está organizado por capas:
- `application`: Lógica de negocio y casos de uso.
- `domain`: Modelos y reglas del negocio (núcleo).
- `infrastructure`: Adaptadores para hardware, archivos y bases de datos.
- `presentation`: Controladores REST y WebSockets.

## 🔧 Configuración y Ejecución

### Requisitos
- Java JDK 17+
- Maven
- Arduino IDE (para cargar el código al microcontrolador)
- Puerto Serial disponible (USB)

### Pasos
1. **Hardware**: Conectar el Arduino con el sketch proporcionado en `DOCUMENTACION_TECNICA.md`.
2. **Base de Datos**: Asegurar que MySQL esté corriendo (o usar configuración por defecto).
3. **Ejecutar Backend**:
   ```bash
   mvn spring-boot:run
   ```
4. **Acceder**: Abrir `http://localhost:8080` en el navegador.

## 📄 Documentación Técnica

Para detalles profundos sobre la arquitectura, protocolos de comunicación y diagramas, consultar el archivo [`DOCUMENTACION_TECNICA.md`](./DOCUMENTACION_TECNICA.md) incluido en este repositorio.

---
*Este software fue diseñado como proyecto de ingeniería informática por Leonardo Fiesco.*
