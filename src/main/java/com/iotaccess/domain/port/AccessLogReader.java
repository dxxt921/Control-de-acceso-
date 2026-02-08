package com.iotaccess.domain.port;

import com.iotaccess.domain.model.AccessRecord;

import java.nio.file.Path;
import java.util.List;

/**
 * Puerto (interfaz) para la lectura de logs de acceso desde archivos.
 */
public interface AccessLogReader {

    /**
     * Lee todos los registros de un archivo CSV.
     * 
     * @param filePath Ruta del archivo a leer
     * @return Lista de registros leídos
     */
    List<AccessRecord> readFromFile(Path filePath);

    /**
     * Obtiene todos los archivos CSV pendientes de procesar.
     * 
     * @return Lista de rutas de archivos
     */
    List<Path> getPendingFiles();

    /**
     * Mueve un archivo procesado al directorio de historial.
     * 
     * @param filePath Ruta del archivo a mover
     * @return Nueva ruta del archivo
     */
    Path moveToHistory(Path filePath);
}
