package com.example.cms_be.config;

import com.example.cms_be.config.intercepter.JwtHandshakeInterceptor;
import com.example.cms_be.handler.LabTimerHandler;
import com.example.cms_be.handler.TerminalHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import com.example.cms_be.ultil.PodLogWebSocketHandler;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer, WebMvcConfigurer {

    private final PodLogWebSocketHandler podLogHandler;
    private final TerminalHandler terminalHandler;
    private final LabTimerHandler labTimerHandler;

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(podLogHandler, "/ws/pod-logs")
                .setAllowedOrigins("*");

        registry.addHandler(terminalHandler, "/api/terminal/{labSessionId}")
                .addInterceptors(new SessionIdInterceptor())
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");

        registry.addHandler(labTimerHandler, "ws/lab-timer/{labSessionId}")
                .addInterceptors(new SessionIdInterceptor())
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
    }



    private static class SessionIdInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
            if (request instanceof ServletServerHttpRequest) {
                String path = ((ServletServerHttpRequest) request).getURI().getPath();
                String labSessionId = path.substring(path.lastIndexOf('/') + 1);
                if (!labSessionId.isEmpty()) {
                    attributes.put("labSessionId", labSessionId);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }
}