package com.iotaccess.infrastructure.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Guarda y recupera el nombre del archivo CSV activo en un archivo binario
 * oculto.
 * El archivo binario no es legible directamente, cumpliendo el requerimiento
 * de que el nombre del archivo "no se vea" y esté en binario.
 */
@Component
@Slf4j
public class BinaryFileTracker {

    @Value("${csv.binary-tracker-path:./data_logs/.file_tracker.dat}")
    private String trackerFilePath;

    /**
     * Guarda el nombre del archivo CSV activo en formato binario.
     *
     * @param fileName Nombre/ruta del archivo CSV activo
     */
    public void saveFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            log.warn("Intento de guardar nombre de archivo vacío en tracker binario");
            return;
        }

        Path path = Paths.get(trackerFilePath);

        try {
            // Crear directorio padre si no existe
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }

            // Escribir en formato binario usando DataOutputStream
            try (DataOutputStream dos = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(path.toFile())))) {
                // Escribir un magic number para identificar el formato
                dos.writeInt(0x494F5446); // "IOTF" en hex
                // Escribir la versión del formato
                dos.writeShort(1);
                // Escribir el timestamp actual
                dos.writeLong(System.currentTimeMillis());
                // Escribir el nombre del archivo
                dos.writeUTF(fileName);
                dos.flush();
            }

            // Marcar como oculto en Windows
            try {
                Path absolutePath = path.toAbsolutePath();
                Runtime.getRuntime().exec(new String[] { "attrib", "+H", absolutePath.toString() });
            } catch (Exception e) {
                log.debug("No se pudo marcar archivo como oculto: {}", e.getMessage());
            }

            log.info("Nombre de archivo guardado en tracker binario: {}", fileName);

        } catch (IOException e) {
            log.error("Error guardando nombre en tracker binario: {}", e.getMessage());
        }
    }

    /**
     * Lee el nombre del archivo CSV activo desde el archivo binario.
     *
     * @return El nombre del archivo, o null si no existe o hay error
     */
    public String readFileName() {
        Path path = Paths.get(trackerFilePath);

        if (!Files.exists(path)) {
            log.debug("Archivo tracker binario no existe: {}", path);
            return null;
        }

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(path.toFile())))) {
            // Verificar magic number
            int magic = dis.readInt();
            if (magic != 0x494F5446) {
                log.warn("Archivo tracker binario con formato inválido (magic: {})", Integer.toHexString(magic));
                return null;
            }
            // Leer versión
            short version = dis.readShort();
            // Leer timestamp
            long timestamp = dis.readLong();
            // Leer nombre del archivo
            String fileName = dis.readUTF();

            log.debug("Nombre leído del tracker binario: {} (versión: {}, timestamp: {})",
                    fileName, version, timestamp);
            return fileName;

        } catch (IOException e) {
            log.error("Error leyendo tracker binario: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Verifica si el archivo tracker binario existe.
     *
     * @return true si existe
     */
    public boolean fileExists() {
        return Files.exists(Paths.get(trackerFilePath));
    }

    /**
     * Obtiene la ruta del archivo tracker.
     */
    public String getTrackerFilePath() {
        return trackerFilePath;
    }
}
