package com.example.cms_be.config;

import com.example.cms_be.handler.TerminalHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.cms_be.ultil.PodLogWebSocketHandler;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer, WebMvcConfigurer {

    private final PodLogWebSocketHandler podLogHandler;

    private final TerminalHandler terminalHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(podLogHandler, "/ws/pod-logs")
                .setAllowedOrigins("*");

        registry.addHandler(this.terminalHandler, "/terminal")
                .setAllowedOrigins("http://localhost:5173/");
    }
}