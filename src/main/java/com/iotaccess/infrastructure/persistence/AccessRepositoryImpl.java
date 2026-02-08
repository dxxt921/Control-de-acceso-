package com.iotaccess.infrastructure.persistence;

import com.iotaccess.domain.model.AccessRecord;
import com.iotaccess.domain.model.AccessStatus;
import com.iotaccess.domain.port.AccessRepository;
import com.iotaccess.infrastructure.persistence.entity.AccessLogEntity;
import com.iotaccess.infrastructure.persistence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementación del puerto AccessRepository usando JPA.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessRepositoryImpl implements AccessRepository {

    private final JpaAccessLogRepository accessLogRepository;
    private final JpaUserRepository userRepository;

    @Override
    @Transactional
    public int saveAll(List<AccessRecord> records) {
        log.info("Guardando {} registros en la base de datos", records.size());

        List<AccessLogEntity> entities = records.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());

        List<AccessLogEntity> saved = accessLogRepository.saveAll(entities);
        log.info("Guardados {} registros exitosamente", saved.size());

        return saved.size();
    }

    @Override
    public List<AccessRecord> findByDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        return accessLogRepository.findByTimestampBetween(startOfDay, endOfDay)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<String> findUserNameByUid(String uid) {
        return userRepository.findByNfcUid(normalizeUid(uid))
                .map(UserEntity::getUserName);
    }

    @Override
    public boolean isUidRegistered(String uid) {
        return userRepository.existsByNfcUid(normalizeUid(uid));
    }

    @Override
    public long countTodayAccesses() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return accessLogRepository.countTodayAccesses(startOfDay);
    }

    /**
     * Convierte un AccessRecord de dominio a una entidad JPA.
     */
    private AccessLogEntity toEntity(AccessRecord record) {
        return AccessLogEntity.builder()
                .uidDetected(record.getUid())
                .accessTimestamp(record.getTimestamp())
                .accessGranted(record.isGranted())
                .stationId(record.getStationId())
                .build();
    }

    /**
     * Convierte una entidad JPA a un AccessRecord de dominio.
     */
    private AccessRecord toDomain(AccessLogEntity entity) {
        AccessStatus status = entity.getAccessGranted()
                ? AccessStatus.GRANTED
                : AccessStatus.DENIED;

        // Buscar nombre de usuario
        String userName = userRepository.findByNfcUid(entity.getUidDetected())
                .map(UserEntity::getUserName)
                .orElse("Desconocido");

        return AccessRecord.builder()
                .id(entity.getId().longValue())
                .uid(entity.getUidDetected())
                .timestamp(entity.getAccessTimestamp())
                .status(status)
                .stationId(entity.getStationId())
                .userName(userName)
                .build();
    }

    /**
     * Normaliza el formato del UID (espacios y mayúsculas).
     */
    private String normalizeUid(String uid) {
        if (uid == null)
            return null;
        return uid.toUpperCase().trim();
    }
}
