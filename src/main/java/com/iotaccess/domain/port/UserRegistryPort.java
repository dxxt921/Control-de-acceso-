package com.iotaccess.domain.port;

import com.iotaccess.domain.model.RegisteredUser;

import java.util.List;
import java.util.Optional;

/**
 * Puerto (interfaz) para la gestión del registro de usuarios.
 * Define el contrato que debe cumplir cualquier implementación de repositorio
 * de usuarios.
 */
public interface UserRegistryPort {

    /**
     * Obtiene todos los usuarios registrados.
     * 
     * @return Lista de usuarios registrados
     */
    List<RegisteredUser> findAll();

    /**
     * Busca un usuario por su UID de NFC.
     * 
     * @param uid UID del dispositivo NFC
     * @return Optional con el usuario si existe
     */
    Optional<RegisteredUser> findByUid(String uid);

    /**
     * Guarda un nuevo usuario en el registro.
     * 
     * @param user Usuario a guardar
     */
    void save(RegisteredUser user);

    /**
     * Elimina un usuario por su UID.
     * 
     * @param uid UID del dispositivo NFC a eliminar
     * @return true si se eliminó correctamente
     */
    boolean delete(String uid);

    /**
     * Verifica si existe un usuario con el UID especificado.
     * 
     * @param uid UID del dispositivo NFC
     * @return true si existe el usuario
     */
    boolean existsByUid(String uid);

    /**
     * Cuenta el total de usuarios registrados.
     * 
     * @return Número de usuarios
     */
    long count();
}
