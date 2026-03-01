package com.iotaccess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IoTAccessApplication {

    public static void main(String[] args) {
        SpringApplication.run(IoTAccessApplication.class, args);
        System.out.println("\n" +
            "╔═══════════════════════════════════════════════════════════╗\n" +
            "║         IoT Access System - Started Successfully         ║\n" +
            "║                                                           ║\n" +
            "║   Dashboard: http://localhost:8080                      ║\n" +
            "║   Configure serial port from the web interface         ║\n" +
            "╚═══════════════════════════════════════════════════════════╝\n"
        );
    }
}
