package com.iotaccess.domain.port;

import com.iotaccess.domain.model.AccessRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Puerto (interfaz) para la persistencia de registros de acceso.
 * Define el contrato que debe cumplir cualquier implementación de repositorio.
 */
public interface AccessRepository {

    /**
     * Guarda una lista de registros de acceso en lote.
     * 
     * @param records Lista de registros a guardar
     * @return Número de registros guardados
     */
    int saveAll(List<AccessRecord> records);

    /**
     * Busca registros de acceso por fecha.
     * 
     * @param date Fecha a buscar
     * @return Lista de registros encontrados
     */
    List<AccessRecord> findByDate(LocalDate date);

    /**
     * Busca un usuario por su UID de NFC.
     * 
     * @param uid UID del tag NFC
     * @return Optional con el nombre del usuario si existe
     */
    Optional<String> findUserNameByUid(String uid);

    /**
     * Verifica si un UID está registrado en el sistema.
     * 
     * @param uid UID del tag NFC
     * @return true si el UID está registrado
     */
    boolean isUidRegistered(String uid);

    /**
     * Cuenta el total de accesos registrados hoy.
     * 
     * @return Número de accesos hoy
     */
    long countTodayAccesses();
}
