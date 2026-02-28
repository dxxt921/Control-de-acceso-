package com.iotaccess.domain.port;

import com.iotaccess.domain.model.AccessRecord;

/**
 * Puerto (interfaz) para la escritura de logs de acceso.
 * Define el contrato para cualquier implementación de escritura de logs.
 */
public interface AccessLogWriter {

    /**
     * Escribe un registro de acceso al log.
     * 
     * @param record Registro a escribir
     */
    void write(AccessRecord record);

    /**
     * Fuerza la escritura de todos los datos en buffer al destino.
     */
    void flush();

    /**
     * Cierra el writer y libera recursos.
     */
    void close();

    /**
     * Inicializa el writer con el nombre de sesión especificado.
     * 
     * @param sessionName Nombre de la sesión para el archivo
     * @return Ruta del archivo creado
     */
    String initialize(String sessionName);

    /**
     * Verifica si el writer está inicializado y listo para escribir.
     * 
     * @return true si está listo
     */
    boolean isReady();

    /**
     * Renombra el archivo CSV activo (y su backup) sin perder datos.
     *
     * @param newSessionName Nuevo nombre de sesión
     * @return Nueva ruta del archivo
     */
    String renameFile(String newSessionName);
}
