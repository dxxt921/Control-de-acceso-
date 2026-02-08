package com.iotaccess.infrastructure.persistence;

import com.iotaccess.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio JPA para operaciones con users.
 */
@Repository
public interface JpaUserRepository extends JpaRepository<UserEntity, Integer> {

    /**
     * Busca un usuario por su UID de NFC.
     * 
     * @param nfcUid UID del tag NFC
     * @return Optional con el usuario si existe
     */
    Optional<UserEntity> findByNfcUid(String nfcUid);

    /**
     * Verifica si existe un usuario con el UID especificado.
     * 
     * @param nfcUid UID del tag NFC
     * @return true si existe
     */
    boolean existsByNfcUid(String nfcUid);
}
