package com.iotaccess.application.service;

import com.iotaccess.application.dto.RegisteredUserDto;
import com.iotaccess.domain.model.RegisteredUser;
import com.iotaccess.domain.port.UserRegistryPort;
import com.iotaccess.infrastructure.persistence.JpaUserRepository;
import com.iotaccess.infrastructure.persistence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de identidad.
 * Gestiona el registro de usuarios en CSV (local) y MySQL (persistente).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityServiceImpl implements IdentityService {

    private final UserRegistryPort userRegistryPort;
    private final JpaUserRepository jpaUserRepository;

    @Override
    public List<RegisteredUserDto> getAllUsers() {
        return userRegistryPort.findAll().stream()
                .map(RegisteredUserDto::fromDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<RegisteredUserDto> findByUid(String uid) {
        return userRegistryPort.findByUid(uid)
                .map(RegisteredUserDto::fromDomain);
    }

    @Override
    public RegisteredUserDto registerUser(String uid, String name) {
        log.info("Registrando nuevo usuario: {} - {}", uid, name);

        // Verificar si ya existe
        if (userRegistryPort.existsByUid(uid)) {
            throw new IllegalArgumentException("El dispositivo ya está registrado: " + uid);
        }

        // Crear nuevo usuario
        RegisteredUser user = RegisteredUser.builder()
                .uid(uid.toUpperCase().trim())
                .name(name.trim())
                .registeredAt(LocalDateTime.now())
                .build();

        // Guardar en CSV (local)
        userRegistryPort.save(user);

        // Guardar DIRECTAMENTE en MySQL
        try {
            if (!jpaUserRepository.existsByNfcUid(user.getUid())) {
                UserEntity entity = UserEntity.builder()
                        .nfcUid(user.getUid())
                        .userName(user.getName())
                        .role("usuario")
                        .createdAt(user.getRegisteredAt())
                        .build();
                jpaUserRepository.save(entity);
                log.info("Usuario insertado en MySQL: {} ({})", user.getUid(), user.getName());
            }
        } catch (Exception e) {
            log.error("Error insertando usuario en MySQL (se reintentará en batch): {}", e.getMessage());
        }

        log.info("Usuario registrado exitosamente: {} - {}", uid, name);
        return RegisteredUserDto.fromDomain(user);
    }

    @Override
    public boolean deleteUser(String uid) {
        log.info("Eliminando usuario: {}", uid);
        boolean deleted = userRegistryPort.delete(uid);

        if (deleted) {
            // Eliminar de MySQL también
            try {
                String normalizedUid = uid.toUpperCase().trim();
                jpaUserRepository.findByNfcUid(normalizedUid)
                        .ifPresent(entity -> {
                            jpaUserRepository.delete(entity);
                            log.info("Usuario eliminado de MySQL: {}", normalizedUid);
                        });
            } catch (Exception e) {
                log.error("Error eliminando usuario de MySQL: {}", e.getMessage());
            }
            log.info("Usuario eliminado: {}", uid);
        } else {
            log.warn("Usuario no encontrado para eliminar: {}", uid);
        }

        return deleted;
    }

    @Override
    public boolean isUserRegistered(String uid) {
        return userRegistryPort.existsByUid(uid);
    }

    @Override
    public Optional<String> getUserNameByUid(String uid) {
        return userRegistryPort.findByUid(uid)
                .map(RegisteredUser::getName);
    }

    @Override
    public long getUserCount() {
        return userRegistryPort.count();
    }
}
