package io.github.candyeer.k8s.config;


import io.github.candyeer.k8s.CommandWebSocketHandler;
import io.github.candyeer.k8s.WebsocketInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * @author lican
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(socketHandler(), "/terminal").addInterceptors(websocketInterceptor());
    }

    @Bean
    public WebSocketHandler socketHandler() {
        return new CommandWebSocketHandler();
    }

    @Bean
    public HandshakeInterceptor websocketInterceptor() {
        return new WebsocketInterceptor();
    }
}
