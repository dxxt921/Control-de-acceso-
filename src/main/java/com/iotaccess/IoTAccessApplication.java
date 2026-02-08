package com.iotaccess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IoT Access System - Aplicación Principal
 * 
 * Sistema de gestión de acceso IoT con:
 * - Lectura de sensor PN532 via Arduino/Serial
 * - Almacenamiento staging en CSV
 * - Procesamiento batch a MySQL a las 10:00 PM
 * - Dashboard web en tiempo real
 */
@SpringBootApplication
@EnableScheduling
public class IoTAccessApplication {

    public static void main(String[] args) {
        SpringApplication.run(IoTAccessApplication.class, args);
        System.out.println("\n" +
            "╔═══════════════════════════════════════════════════════════╗\n" +
            "║         IoT Access System - Started Successfully         ║\n" +
            "║                                                           ║\n" +
            "║  🌐 Dashboard: http://localhost:8080                      ║\n" +
            "║  📡 Configure serial port from the web interface         ║\n" +
            "╚═══════════════════════════════════════════════════════════╝\n"
        );
    }
}
