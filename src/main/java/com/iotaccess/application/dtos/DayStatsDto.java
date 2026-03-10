package com.iotaccess.application.dtos;

/**
 * DTO para estadísticas del día.
 * Extraído de AccessService para mantener los DTOs centralizados.
 */
public record DayStatsDto(
        long totalAccesses,
        long grantedCount,
        long deniedCount,
        String lastAccessTime) {
}
