package com.iotaccess.infrastructure.file;

import com.iotaccess.domain.model.RegisteredUser;
import com.iotaccess.domain.port.UserRegistryPort;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Adaptador para gestionar el archivo user_registry.csv.
 * Implementa lectura/escritura thread-safe del registro de usuarios.
 * 
 * ENTREGABLE 3:
 * - Escribe simultáneamente al CSV normal y al CSV de respaldo.
 * - Si el archivo principal no existe al cargar, intenta recuperar del backup.
 */
@Component
@Slf4j
public class CsvUserRegistryAdapter implements UserRegistryPort {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] CSV_HEADER = { "uid", "name", "registered_at" };

    @Value("${csv.user-registry-path:./data_logs/user_registry.csv}")
    private String userRegistryPath;

    @Value("${csv.backup-user-registry-path:./data_logs_backup/user_registry_backup.csv}")
    private String backupUserRegistryPath;

    // Cache en memoria para acceso rápido (thread-safe)
    private final CopyOnWriteArrayList<RegisteredUser> usersCache = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        // Cargar datos existentes en memoria (del backup si existe)
        loadFromFile();

        // Borrar archivos viejos para recrearlos dinámicamente
        deleteFileIfExists(userRegistryPath);
        deleteFileIfExists(backupUserRegistryPath);

        // Crear archivos nuevos
        ensureFileExists();

        // Restaurar datos de usuarios al archivo nuevo
        if (!usersCache.isEmpty()) {
            saveToFile();
        }

        log.info("✓ user_registry.csv creado al iniciar programa con {} usuarios", usersCache.size());
    }

    /**
     * Elimina un archivo si existe.
     */
    private void deleteFileIfExists(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.debug("Archivo eliminado para recrear: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("No se pudo eliminar {}: {}", filePath, e.getMessage());
        }
    }

    /**
     * Asegura que el archivo CSV existe con el header correcto.
     */
    private void ensureFileExists() {
        ensureSingleFileExists(userRegistryPath);
        ensureSingleFileExists(backupUserRegistryPath);
    }

    /**
     * Asegura que un archivo CSV individual existe con el header correcto.
     */
    private void ensureSingleFileExists(String filePath) {
        Path path = Paths.get(filePath);

        try {
            // Crear directorios si no existen
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
                log.info("Directorio creado: {}", path.getParent());
            }

            // Crear archivo con header si no existe
            if (!Files.exists(path)) {
                try (CSVWriter writer = new CSVWriter(new FileWriter(path.toFile()))) {
                    writer.writeNext(CSV_HEADER);
                }
                log.info("Archivo CSV creado: {}", path);
            }
        } catch (IOException e) {
            log.error("Error creando archivo CSV {}: {}", filePath, e.getMessage());
        }
    }

    /**
     * Carga usuarios desde el archivo CSV a la cache en memoria.
     * Si el archivo principal no existe, intenta cargar desde el backup.
     */
    private synchronized void loadFromFile() {
        usersCache.clear();
        Path path = Paths.get(userRegistryPath);
        Path backupPath = Paths.get(backupUserRegistryPath);

        // ENTREGABLE 3: Resiliencia - si el principal no existe, intentar backup
        if (!Files.exists(path)) {
            log.warn("Archivo user_registry.csv no encontrado, intentando restaurar desde backup...");
            if (Files.exists(backupPath)) {
                try {
                    // Asegurar directorio padre
                    if (path.getParent() != null && !Files.exists(path.getParent())) {
                        Files.createDirectories(path.getParent());
                    }
                    Files.copy(backupPath, path, StandardCopyOption.REPLACE_EXISTING);
                    log.info("✓ user_registry.csv restaurado desde backup exitosamente");
                } catch (IOException e) {
                    log.error("Error restaurando user_registry desde backup: {}", e.getMessage());
                    return;
                }
            } else {
                log.warn("Ni el archivo principal ni el backup de user_registry existen");
                return;
            }
        }

        try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
            List<String[]> lines = reader.readAll();

            // Saltar header
            for (int i = 1; i < lines.size(); i++) {
                try {
                    RegisteredUser user = parseLine(lines.get(i));
                    if (user != null) {
                        usersCache.add(user);
                    }
                } catch (Exception e) {
                    log.warn("Error parseando línea {} del archivo: {}", i + 1, e.getMessage());
                }
            }

            log.info("Cargados {} usuarios desde user_registry.csv", usersCache.size());

        } catch (IOException | CsvException e) {
            log.error("Error leyendo user_registry.csv: {}", e.getMessage());
        }
    }

    /**
     * Guarda todos los usuarios de la cache al archivo CSV principal y al de
     * respaldo.
     */
    private synchronized void saveToFile() {
        // Guardar en archivo principal
        saveToSingleFile(userRegistryPath);

        // ENTREGABLE 3: Guardar también en archivo de respaldo
        saveToSingleFile(backupUserRegistryPath);
    }

    /**
     * Guarda todos los usuarios de la cache a un archivo CSV específico.
     */
    private void saveToSingleFile(String filePath) {
        Path path = Paths.get(filePath);

        try {
            // Asegurar directorio padre
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }

            try (CSVWriter writer = new CSVWriter(new FileWriter(path.toFile()))) {
                // Escribir header
                writer.writeNext(CSV_HEADER);

                // Escribir cada usuario
                for (RegisteredUser user : usersCache) {
                    String[] line = {
                            user.getUid(),
                            user.getName(),
                            user.getRegisteredAt() != null
                                    ? user.getRegisteredAt().format(TIMESTAMP_FORMAT)
                                    : ""
                    };
                    writer.writeNext(line);
                }

                log.info("Guardados {} usuarios en {}", usersCache.size(), filePath);
            }

        } catch (IOException e) {
            log.error("Error escribiendo {}: {}", filePath, e.getMessage());
        }
    }

    /**
     * Parsea una línea CSV a un RegisteredUser.
     */
    private RegisteredUser parseLine(String[] fields) {
        if (fields.length < 2) {
            return null;
        }

        String uid = fields[0].trim().toUpperCase();
        String name = fields[1].trim();

        LocalDateTime registeredAt = null;
        if (fields.length > 2 && !fields[2].trim().isEmpty()) {
            try {
                registeredAt = LocalDateTime.parse(fields[2].trim(), TIMESTAMP_FORMAT);
            } catch (DateTimeParseException e) {
                log.debug("Error parseando fecha: {}", fields[2]);
            }
        }

        return RegisteredUser.builder()
                .uid(uid)
                .name(name)
                .registeredAt(registeredAt)
                .build();
    }

    @Override
    public List<RegisteredUser> findAll() {
        return new ArrayList<>(usersCache);
    }

    @Override
    public Optional<RegisteredUser> findByUid(String uid) {
        String normalizedUid = normalizeUid(uid);
        return usersCache.stream()
                .filter(u -> u.getUid().equals(normalizedUid))
                .findFirst();
    }

    @Override
    public void save(RegisteredUser user) {
        String normalizedUid = normalizeUid(user.getUid());
        user.setUid(normalizedUid);

        // Remover si ya existe (para actualizar)
        usersCache.removeIf(u -> u.getUid().equals(normalizedUid));

        // Agregar nuevo/actualizado
        usersCache.add(user);

        // Persistir a disco (principal + respaldo)
        saveToFile();

        log.info("Usuario guardado: {} - {}", user.getUid(), user.getName());
    }

    @Override
    public boolean delete(String uid) {
        String normalizedUid = normalizeUid(uid);
        boolean removed = usersCache.removeIf(u -> u.getUid().equals(normalizedUid));

        if (removed) {
            saveToFile();
            log.info("Usuario eliminado: {}", normalizedUid);
        }

        return removed;
    }

    @Override
    public boolean existsByUid(String uid) {
        String normalizedUid = normalizeUid(uid);
        return usersCache.stream()
                .anyMatch(u -> u.getUid().equals(normalizedUid));
    }

    @Override
    public long count() {
        return usersCache.size();
    }

    /**
     * Recarga los usuarios desde el archivo (útil para sincronización).
     */
    public void reload() {
        loadFromFile();
    }

    /**
     * Obtiene la ruta del archivo de registro.
     */
    public String getFilePath() {
        return userRegistryPath;
    }

    /**
     * Normaliza el formato del UID.
     */
    private String normalizeUid(String uid) {
        if (uid == null)
            return "";
        return uid.toUpperCase().trim();
    }
}
