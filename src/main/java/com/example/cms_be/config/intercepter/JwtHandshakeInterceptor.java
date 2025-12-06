package com.example.cms_be.config.intercepter;

import com.example.cms_be.security.jwt.JwtUtils;
import com.example.cms_be.security.service.UserDetailsServiceImpl; // Hoặc UserDetailsService interface
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService; // Cần service này để load user

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpServletRequest httpRequest = servletRequest.getServletRequest();

            String token = httpRequest.getParameter("token");

            if (token != null && !token.isEmpty()) {
                try {
                    String username = jwtUtils.getUserNameFromToken(token);

                    if (username != null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                        if (jwtUtils.validateToken(token, userDetails)) {

                            attributes.put("username", username);

                            Integer userId = jwtUtils.getUserIdFromToken(token);
                            if (userId != null) {
                                attributes.put("userId", userId);
                            }

                            String path = request.getURI().getPath();
                            String labSessionId = path.substring(path.lastIndexOf('/') + 1);
                            attributes.put("labSessionId", labSessionId);

                            log.info("WebSocket connection authorized for user: {}", username);
                            return true;
                        }
                    }
                } catch (Exception e) {
                    log.error("WebSocket Authentication failed: {}", e.getMessage());
                }
            }
        }

        log.warn("WebSocket connection rejected: No valid token found.");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}