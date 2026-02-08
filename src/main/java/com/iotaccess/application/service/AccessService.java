package com.iotaccess.application.service;

import com.iotaccess.application.dto.AccessRecordDto;
import com.iotaccess.application.dto.SessionStatusDto;
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
     * Obtiene estadísticas del día.
     * 
     * @return DTO con estadísticas
     */
    DayStatsDto getDayStats();

    /**
     * DTO interno para estadísticas del día.
     */
    record DayStatsDto(
            long totalAccesses,
            long grantedCount,
            long deniedCount,
            String lastAccessTime) {
    }
}
