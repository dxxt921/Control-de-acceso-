package com.iotaccess.presentation.config;

import com.iotaccess.presentation.websocket.AccessWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
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
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(accessWebSocketHandler, "/ws/access")
                .setAllowedOrigins("*"); // En producción, restringir a dominio específico
    }
}
