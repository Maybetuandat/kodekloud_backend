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
        // 1. Kiểm tra xem request có phải là ServletRequest không (để lấy query param dễ dàng)
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpServletRequest httpRequest = servletRequest.getServletRequest();

            // 2. Lấy token từ Query Parameter: ws://...?token=eyJ...
            String token = httpRequest.getParameter("token");

            // 3. Logic xác thực với JwtUtils của bạn
            if (token != null && !token.isEmpty()) {
                try {
                    // Bước A: Parse lấy username trước
                    String username = jwtUtils.getUserNameFromToken(token);

                    if (username != null) {
                        // Bước B: Load UserDetails từ DB
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                        // Bước C: Validate token bằng hàm của bạn (check chữ ký + hạn + match username)
                        if (jwtUtils.validateToken(token, userDetails)) {

                            // Token ngon lành -> Lưu thông tin vào session attributes
                            attributes.put("username", username);

                            // (Optional) Lấy thêm userId nếu cần
                            Integer userId = jwtUtils.getUserIdFromToken(token);
                            if (userId != null) {
                                attributes.put("userId", userId);
                            }

                            // (Optional) Lấy labSessionId từ URL path
                            String path = request.getURI().getPath();
                            // Giả sử URL dạng /ws/lab-timer/123
                            String labSessionId = path.substring(path.lastIndexOf('/') + 1);
                            attributes.put("labSessionId", labSessionId);

                            log.info("WebSocket connection authorized for user: {}", username);
                            return true; // OK, cho phép kết nối
                        }
                    }
                } catch (Exception e) {
                    log.error("WebSocket Authentication failed: {}", e.getMessage());
                }
            }
        }

        log.warn("WebSocket connection rejected: No valid token found.");
        return false; // Từ chối kết nối
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Không cần làm gì ở đây
    }
}