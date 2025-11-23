package com.example.cms_be.ultil;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class PodLogWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    
    // Lưu trữ các session theo sessionId
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // Lưu trữ mapping giữa sessionId và podName để filter messages
    private final ConcurrentHashMap<String, String> sessionPodMapping = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        
        // Lấy podName từ query parameters
        String query = session.getUri().getQuery();
        String podName = extractPodNameFromQuery(query);
        
        if (podName != null) {
            sessionPodMapping.put(sessionId, podName);
            log.info("WebSocket connection established for session {} with podName {}", sessionId, podName);
            
            // Gửi message confirmation
            sendMessage(session, new WebSocketMessage("connection", 
                "Connected to pod logs stream for: " + podName, null));
        } else {
            log.warn("WebSocket connection established but no podName provided for session {}", sessionId);
            session.close(CloseStatus.BAD_DATA.withReason("podName parameter is required"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        sessionPodMapping.remove(sessionId);
        log.info("WebSocket connection closed for session {}: {}", sessionId, status.toString());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        
        // ✅ Chỉ log warning cho Broken Pipe và Connection Reset (không phải error)
        if (exception.getMessage() != null && 
            (exception.getMessage().contains("Broken pipe") || 
             exception.getMessage().contains("Connection reset"))) {
            log.warn("WebSocket connection lost for session {}: {}", sessionId, exception.getMessage());
        } else {
            log.error("WebSocket transport error for session {}: {}", sessionId, exception.getMessage());
        }
        
        // Clean up
        sessions.remove(sessionId);
        sessionPodMapping.remove(sessionId);
        
        // ✅ Close session safely
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (Exception e) {
            log.debug("Error closing session after transport error: {}", e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Có thể handle các message từ client nếu cần (như pause/resume logs)
        log.debug("Received message from client {}: {}", session.getId(), message.getPayload());
    }

    /**
     * Gửi log message đến tất cả clients đang theo dõi pod cụ thể
     */
    public void broadcastLogToPod(String podName, String logType, String message, Object data) {
        WebSocketMessage wsMessage = new WebSocketMessage(logType, message, data);
        
        // ✅ Sử dụng removeIf để tự động clean up dead sessions
        sessions.entrySet().removeIf(entry -> {
            String sessionId = entry.getKey();
            WebSocketSession session = entry.getValue();
            String sessionPodName = sessionPodMapping.get(sessionId);
            
            // Chỉ gửi cho sessions đang subscribe pod này
            if (podName.equals(sessionPodName)) {
                try {
                    if (session.isOpen()) {
                        sendMessage(session, wsMessage);
                        return false; // Giữ session này
                    } else {
                        log.debug("Removing closed session: {}", sessionId);
                        sessionPodMapping.remove(sessionId);
                        return true; // Xóa session đã đóng
                    }
                } catch (IOException e) {
                    // ✅ Chỉ log warning cho Broken Pipe
                    if (e.getMessage() != null && 
                        (e.getMessage().contains("Broken pipe") || 
                         e.getMessage().contains("Connection reset"))) {
                        log.debug("Client disconnected (session {}): {}", sessionId, e.getMessage());
                    } else {
                        log.warn("Failed to send message to session {}: {}", sessionId, e.getMessage());
                    }
                    sessionPodMapping.remove(sessionId);
                    return true; // Xóa session lỗi
                }
            }
            return false;
        });
    }

    /**
     * Gửi message đến một session cụ thể (thread-safe)
     */
    private void sendMessage(WebSocketSession session, WebSocketMessage message) throws IOException {
        if (session.isOpen()) {
            synchronized (session) { // ✅ Thread-safe cho việc gửi message
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            }
        }
    }

    /**
     * Extract podName từ query string
     */
    private String extractPodNameFromQuery(String query) {
        if (query == null) return null;
        
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && "podName".equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }

    /**
     * Kiểm tra xem có session nào đang theo dõi pod này không
     */
    public boolean hasActiveSessionsForPod(String podName) {
        return sessionPodMapping.containsValue(podName);
    }

    /**
     * Class đại diện cho WebSocket message
     */
    public static class WebSocketMessage {
        public String type;
        public String message;
        public Object data;
        public long timestamp;

        public WebSocketMessage(String type, String message, Object data) {
            this.type = type;
            this.message = message;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters and setters for JSON serialization
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}