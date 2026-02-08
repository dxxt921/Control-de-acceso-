package com.iotaccess.application.service;

import com.iotaccess.application.dto.RegisteredUserDto;

import java.util.List;
import java.util.Optional;

/**
 * Servicio para la gestión de identidades de usuarios.
 * Maneja el registro de usuarios en user_registry.csv.
 */
public interface IdentityService {

    /**
     * Obtiene todos los usuarios registrados.
     * 
     * @return Lista de DTOs de usuarios
     */
    List<RegisteredUserDto> getAllUsers();

    /**
     * Busca un usuario por su UID.
     * 
     * @param uid UID del dispositivo NFC
     * @return Optional con el DTO del usuario si existe
     */
    Optional<RegisteredUserDto> findByUid(String uid);

    /**
     * Registra un nuevo usuario.
     * 
     * @param uid  UID del dispositivo NFC
     * @param name Nombre del usuario
     * @return DTO del usuario registrado
     */
    RegisteredUserDto registerUser(String uid, String name);

    /**
     * Elimina un usuario por su UID.
     * 
     * @param uid UID del dispositivo a eliminar
     * @return true si se eliminó correctamente
     */
    boolean deleteUser(String uid);

    /**
     * Verifica si un UID está registrado.
     * 
     * @param uid UID a verificar
     * @return true si está registrado
     */
    boolean isUserRegistered(String uid);

    /**
     * Obtiene el nombre de un usuario por su UID.
     * 
     * @param uid UID del dispositivo
     * @return Optional con el nombre si existe
     */
    Optional<String> getUserNameByUid(String uid);

    /**
     * Cuenta el total de usuarios registrados.
     * 
     * @return Número de usuarios
     */
    long getUserCount();
}
