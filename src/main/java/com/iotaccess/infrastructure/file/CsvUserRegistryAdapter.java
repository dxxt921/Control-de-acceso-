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
 */
@Component
@Slf4j
public class CsvUserRegistryAdapter implements UserRegistryPort {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] CSV_HEADER = { "uid", "name", "registered_at" };

    @Value("${csv.user-registry-path:./data_logs/user_registry.csv}")
    private String userRegistryPath;

    // Cache en memoria para acceso rápido (thread-safe)
    private final CopyOnWriteArrayList<RegisteredUser> usersCache = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        ensureFileExists();
        loadFromFile();
    }

    /**
     * Asegura que el archivo CSV existe con el header correcto.
     */
    private void ensureFileExists() {
        Path path = Paths.get(userRegistryPath);

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
                log.info("Archivo user_registry.csv creado: {}", path);
            }
        } catch (IOException e) {
            log.error("Error creando archivo user_registry.csv: {}", e.getMessage());
        }
    }

    /**
     * Carga usuarios desde el archivo CSV a la cache en memoria.
     */
    private synchronized void loadFromFile() {
        usersCache.clear();
        Path path = Paths.get(userRegistryPath);

        if (!Files.exists(path)) {
            log.warn("Archivo user_registry.csv no encontrado");
            return;
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
     * Guarda todos los usuarios de la cache al archivo CSV.
     */
    private synchronized void saveToFile() {
        Path path = Paths.get(userRegistryPath);

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

            log.info("Guardados {} usuarios en user_registry.csv", usersCache.size());

        } catch (IOException e) {
            log.error("Error escribiendo user_registry.csv: {}", e.getMessage());
            throw new RuntimeException("Error guardando registro de usuarios", e);
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

        // Persistir a disco
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
