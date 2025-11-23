package com.example.cms_be.handler;

import com.example.cms_be.dto.connection.SshConnectionDetails;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.UserLabSessionRepository;
import com.example.cms_be.service.KubernetesDiscoveryService;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class TerminalHandler extends TextWebSocketHandler {

    // --- DEPENDENCIES ---
    private final KubernetesDiscoveryService discoveryService;
    private final UserLabSessionRepository userLabSessionRepository;

    // --- STATE MANAGEMENT: Quản lý các kết nối SSH cho mỗi WebSocket session ---
    private final Map<String, Session> sshSessions = new ConcurrentHashMap<>();
    private final Map<String, ChannelShell> sshChannels = new ConcurrentHashMap<>();
    private final Map<String, OutputStream> sshOutputStreams = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String wsSessionId = session.getId();
        log.info("✅ WebSocket client connected: {}", wsSessionId);

        try {
            // 1. Lấy labSessionId từ attributes mà Interceptor đã đặt
            Object labSessionIdAttr = session.getAttributes().get("labSessionId");
            if (labSessionIdAttr == null) {
                throw new IllegalArgumentException("labSessionId is missing from WebSocket session attributes.");
            }
            int labSessionId = Integer.parseInt(labSessionIdAttr.toString());
            log.info("Attempting to establish terminal for lab session ID: {}", labSessionId);

            // 2. Tìm thông tin máy ảo từ database
            UserLabSession userLabSession = userLabSessionRepository.findById(labSessionId)
                    .orElseThrow(() -> new RuntimeException("UserLabSession not found for ID: " + labSessionId));
            String vmName = "vm-" + userLabSession.getId();
            String namespace = userLabSession.getLab().getNamespace();

            log.info("Found VM details - Name: {}, Namespace: {}", vmName, namespace);
            // 3. Dùng Discovery Service để lấy thông tin kết nối SSH từ bên ngoài
            SshConnectionDetails details = discoveryService.getExternalSshDetails(vmName, namespace);

            // 4. Mở kết nối SSH và một 'shell' channel
            JSch jsch = new JSch();
            Session jschSession = jsch.getSession("ubuntu", details.host(), details.port());
            jschSession.setPassword("1234");
            jschSession.setConfig("StrictHostKeyChecking", "no");
            jschSession.connect(20000); // 20s connection timeout

            ChannelShell channel = (ChannelShell) jschSession.openChannel("shell");
            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();
            channel.connect(10000); // 10s channel connection timeout

            log.info("✅ SSH shell channel created for WebSocket session: {}", wsSessionId);

            // 5. Nối kết luồng output từ SSH đến WebSocket client
            // Tạo một luồng riêng để đọc dữ liệu từ máy ảo và gửi cho client
            new Thread(() -> {
                try {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while (channel.isConnected() && (bytesRead = in.read(buffer)) != -1) {
                        session.sendMessage(new TextMessage(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8)));
                        System.out.println(new TextMessage(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8)));
                    }
                } catch (Exception e) {
                    log.warn("Error reading from SSH stream for session {}, closing connection.", wsSessionId, e);
                } finally {
                    cleanup(wsSessionId);
                }
            }).start();
            // 6. Lưu lại các đối tượng cần thiết để quản lý phiên
            sshSessions.put(wsSessionId, jschSession);
            sshChannels.put(wsSessionId, channel);
            sshOutputStreams.put(wsSessionId, out);

        } catch (Exception e) {
            log.error("🚨 Failed to establish terminal connection for session {}: {}", wsSessionId, e.getMessage(), e);
            try {
                session.sendMessage(new TextMessage("\r\n🚨 Error: Could not connect to the lab environment. Details: " + e.getMessage()));
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ioEx) {
                // Ignore
            }
            cleanup(wsSessionId);
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Lấy output stream của SSH channel tương ứng và ghi dữ liệu người dùng gõ vào
        OutputStream out = sshOutputStreams.get(session.getId());
        if (out != null) {
            try {
                out.write(message.getPayload().getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                log.error("Error writing to SSH stream for session {}: {}", session.getId(), e.getMessage());
                cleanup(session.getId());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("❌ WebSocket client disconnected: {} | Status: {}", session.getId(), status);
        cleanup(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("🚨 Transport error for session {}: {}", session.getId(), exception.getMessage());
        cleanup(session.getId());
    }

    /**
     * Dọn dẹp tài nguyên (đóng kết nối SSH) cho một session cụ thể.
     */
    private void cleanup(String sessionId) {
        log.info("🧹 Cleaning up SSH resources for WebSocket session: {}", sessionId);

        // Đóng Output Stream
        OutputStream out = sshOutputStreams.remove(sessionId);
        if (out != null) {
            try { out.close(); } catch (IOException ignored) {}
        }

        // Đóng Channel
        ChannelShell channel = sshChannels.remove(sessionId);
        if (channel != null) {
            channel.disconnect();
        }

        // Đóng Session
        Session sshSession = sshSessions.remove(sessionId);
        if (sshSession != null) {
            sshSession.disconnect();
        }

        log.info("✅ Cleanup complete for session: {}", sessionId);
    }
}