package com.iotaccess.presentation.configs;

import com.iotaccess.presentation.websockets.AccessWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configuración de WebSocket para la aplicación.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AccessWebSocketHandler accessWebSocketHandler;

    @Override
    @SuppressWarnings("null")
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(accessWebSocketHandler, "/ws/access")
                .setAllowedOrigins("*"); // En producción, restringir a dominio específico
    }
}
