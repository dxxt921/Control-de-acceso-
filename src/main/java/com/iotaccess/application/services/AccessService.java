package com.iotaccess.application.services;

import com.iotaccess.application.dtos.AccessRecordDto;
import com.iotaccess.application.dtos.DayStatsDto;
import com.iotaccess.application.dtos.SessionStatusDto;
import com.iotaccess.domain.model.SerialPortInfo;

import java.util.List;

/**
 * Interfaz del servicio principal de acceso.
 * Define la lógica de negocio de la aplicación.
 */
public interface AccessService {

    /**
     * Obtiene la lista de puertos seriales disponibles.
     * 
     * @return Lista de información de puertos
     */
    List<SerialPortInfo> getAvailablePorts();

    /**
     * Inicia una sesión de captura.
     * 
     * @param portName    Nombre del puerto COM
     * @param sessionName Nombre para la sesión/archivo
     * @throws IllegalStateException si ya hay una sesión activa
     */
    void startSession(String portName, String sessionName);

    /**
     * Reconecta el Arduino a una sesión existente sin crear nuevo CSV.
     * Permite continuar escribiendo en el mismo archivo después de
     * desconectar y reconectar el Arduino.
     *
     * @param portName Nombre del puerto COM
     */
    void reconnectSession(String portName);

    /**
     * Detiene la sesión de captura actual.
     */
    void stopSession();

    /**
     * Obtiene el estado de la sesión actual.
     * 
     * @return DTO con el estado de la sesión
     */
    SessionStatusDto getSessionStatus();

    /**
     * Obtiene los registros de acceso del día actual desde el CSV.
     * 
     * @return Lista de DTOs de registros
     */
    List<AccessRecordDto> getTodayRecords();

    /**
     * Obtiene los últimos N registros.
     * 
     * @param limit Número máximo de registros
     * @return Lista de DTOs de registros
     */
    List<AccessRecordDto> getLatestRecords(int limit);

    /**
     * Procesa un UID recibido del sensor.
     * Este método es llamado por el listener serial.
     * 
     * @param uid UID del tag NFC
     */
    void processIncomingUid(String uid);

    /**
     * Renombra la sesión actual (renombra el CSV sin perder datos).
     *
     * @param newName Nuevo nombre de sesión
     */
    void renameCurrentSession(String newName);

    /**
     * Obtiene estadísticas del día.
     * 
     * @return DTO con estadísticas
     */
    DayStatsDto getDayStats();

    /**
     * Obtiene una lista de los nombres de los archivos CSV de sesiones recientes.
     *
     * @return Lista de nombres de archivos CSV (sin la ruta completa).
     */
    List<String> getRecentSessions();

    /**
     * Reconecta el Arduino y reanuda la escritura en una sesión CSV específica.
     *
     * @param portName    Nombre del puerto COM
     * @param sessionFile Nombre del archivo CSV existente
     */
    void reconnectSession(String portName, String sessionFile);

    /**
     * Obtiene el historial de cambios de nombre de sesiones.
     *
     * @return Lista de mapas con las claves timestamp, old_name, new_name
     */
    java.util.List<java.util.Map<String, String>> getRenameHistory();
}
