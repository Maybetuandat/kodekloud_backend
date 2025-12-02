package com.example.cms_be.handler;

import com.example.cms_be.dto.connection.SshConnectionDetails;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.UserLabSessionRepository;
import com.example.cms_be.service.KubernetesDiscoveryService;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.kubernetes.client.PortForward;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Pod;
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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class TerminalHandler extends TextWebSocketHandler {

    // --- DEPENDENCIES ---
    private final KubernetesDiscoveryService discoveryService;
    private final UserLabSessionRepository userLabSessionRepository;
    private final ApiClient apiClient;

    // --- STATE MANAGEMENT: Quản lý các kết nối SSH cho mỗi WebSocket session ---
    private final Map<String, Session> sshSessions = new ConcurrentHashMap<>();
    private final Map<String, ChannelShell> sshChannels = new ConcurrentHashMap<>();
    private final Map<String, OutputStream> sshOutputStreams = new ConcurrentHashMap<>();
    private final Map<String, ServerSocket> portForwardSockets = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String wsSessionId = session.getId();
        try {
            Object labSessionIdAttr = session.getAttributes().get("labSessionId");
            if (labSessionIdAttr == null) return;
            int labSessionId = Integer.parseInt(labSessionIdAttr.toString());

            UserLabSession userLabSession = userLabSessionRepository.findById(labSessionId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));

            if (!"READY".equals(userLabSession.getStatus())) {
                session.sendMessage(new TextMessage("Lab is not ready."));
                session.close();
                return;
            }

            // BẮT ĐẦU KẾT NỐI QUA TUNNEL
            connectViaTunnel(session, userLabSession, wsSessionId);

        } catch (Exception e) {
            log.error("Connection failed", e);
            cleanup(wsSessionId);
        }
    }

    private void connectViaTunnel(WebSocketSession wsSession, UserLabSession labSession, String wsSessionId) {
        ServerSocket serverSocket = null;
        try {
            String vmName = "vm-" + labSession.getId();
            String namespace = labSession.getLab().getNamespace();

            // 1. Lấy Pod Name
            V1Pod pod = discoveryService.waitForPodRunning(vmName, namespace, 10);
            String podName = pod.getMetadata().getName();
            log.info("[{}] Target Pod: {}", wsSessionId, podName);

            // 2. Tạo ServerSocket để lắng nghe cục bộ (Làm cầu nối cho JSch)
            // Port 0 để OS tự chọn port rảnh
            serverSocket = new ServerSocket(0);
            int localPort = serverSocket.getLocalPort();
            portForwardSockets.put(wsSessionId, serverSocket); // Lưu để cleanup

            log.info("[{}] Local Bridge Server started on port {}", wsSessionId, localPort);

            // 3. Khởi chạy luồng Bridge (Nối JSch <-> K8s PortForward)
            // Cần biến final để dùng trong lambda
            final ServerSocket bridgeServer = serverSocket;

            CompletableFuture.runAsync(() -> {
                try {
                    // Chờ JSch kết nối vào (accept sẽ block cho đến khi bước 4 chạy)
                    Socket jschSocket = bridgeServer.accept();

                    // Khởi tạo K8s PortForward Stream
                    PortForward forward = new PortForward(apiClient);
                    // FIX LỖI COMPILE: Dùng List<Integer> chỉ chứa port đích (22)
                    PortForward.PortForwardResult result = forward.forward(namespace, podName, Collections.singletonList(22));

                    // Lấy stream 2 chiều
                    InputStream k8sIn = result.getInputStream(22);
                    OutputStream k8sOut = result.getOutboundStream(22);

                    InputStream jschIn = jschSocket.getInputStream();
                    OutputStream jschOut = jschSocket.getOutputStream();

                    log.info("[{}] Tunnel pipes connected. Bridging data...", wsSessionId);

                    // Pipe 1: JSch (Client) -> K8s (Pod)
                    CompletableFuture.runAsync(() -> {
                        try {
                            jschIn.transferTo(k8sOut); // Java 9+ method
                        } catch (IOException e) {
                            // Connection closed logic
                        }
                    });

                    // Pipe 2: K8s (Pod) -> JSch (Client)
                    try {
                        k8sIn.transferTo(jschOut);
                    } catch (IOException e) {
                        // Connection closed logic
                    }

                } catch (Exception e) {
                    log.error("[{}] Bridge error: {}", wsSessionId, e.getMessage());
                }
            });

            // 4. JSch Connect tới LOCALHOST (vừa mở ở bước 2)
            JSch jsch = new JSch();
            // Kết nối vào chính port serverSocket đang lắng nghe
            Session jschSession = jsch.getSession("ubuntu", "127.0.0.1", localPort);
            jschSession.setPassword("1234");
            jschSession.setConfig("StrictHostKeyChecking", "no");

            // Timeout kết nối
            jschSession.connect(10000);

            log.info("[{}] JSch connected to Local Bridge successfully", wsSessionId);

            // 5. Setup kênh Shell (như cũ)
            ChannelShell channel = (ChannelShell) jschSession.openChannel("shell");
            channel.setPty(true);

            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();
            channel.connect();

            // Lưu state
            sshSessions.put(wsSessionId, jschSession);
            sshChannels.put(wsSessionId, channel);
            sshOutputStreams.put(wsSessionId, out);

            wsSession.sendMessage(new TextMessage("\r\n🚀 Connected via K8s Secure Tunnel!\r\n"));

            // Luồng đọc output từ SSH trả về Web Client
            CompletableFuture.runAsync(() -> {
                try {
                    byte[] buffer = new byte[4096];
                    int i;
                    while ((i = in.read(buffer)) != -1) {
                        wsSession.sendMessage(new TextMessage(new String(buffer, 0, i, StandardCharsets.UTF_8)));
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    cleanup(wsSessionId);
                }
            });

        } catch (Exception e) {
            log.error("[{}] Tunnel setup failed: {}", wsSessionId, e.getMessage());
            try {
                if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
                wsSession.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ex) {}
        }
    }
    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("No free ports", e);
        }
    }
    /**
     * 🔥 NEW: Poll session status periodically for labs that are not ready yet
     */
    private void startStatusPolling(WebSocketSession session, int labSessionId) {
        CompletableFuture.runAsync(() -> {
            int maxPolls = 60; // 5 minutes maximum
            int pollInterval = 5000; // 5 seconds
            
            for (int i = 0; i < maxPolls; i++) {
                try {
                    Thread.sleep(pollInterval);
                    
                    if (!session.isOpen()) {
                        log.info("WebSocket session closed during polling for lab session {}", labSessionId);
                        break;
                    }
                    
                    UserLabSession userLabSession = userLabSessionRepository.findById(labSessionId)
                            .orElse(null);
                    
                    if (userLabSession == null) {
                        session.sendMessage(new TextMessage("\r\n❌ Lab session not found."));
                        session.close(CloseStatus.NORMAL);
                        break;
                    }
                    
                    String currentStatus = userLabSession.getStatus();
                    log.debug("Polling lab session {} - current status: {}", labSessionId, currentStatus);
                    
                    if ("READY".equals(currentStatus)) {
                        log.info("Lab session {} is now READY! Establishing SSH connection...", labSessionId);
                        session.sendMessage(new TextMessage("\r\n✅ Lab is ready! Connecting to terminal...\r\n"));
                        establishSSHConnectionForReadyLab(session, userLabSession, session.getId());
                        break;
                    } else if ("SETUP_FAILED".equals(currentStatus) || "FAILED".equals(currentStatus)) {
                        session.sendMessage(new TextMessage("\r\n❌ Lab setup failed. Please try again.\r\n"));
                        session.close(CloseStatus.NORMAL.withReason("Lab setup failed"));
                        break;
                    }
                    
                    // Send periodic status updates
                    if (i % 2 == 0) { // Every 10 seconds
                        String statusMessage = getStatusMessage(currentStatus);
                        session.sendMessage(new TextMessage(statusMessage));
                    }
                    
                } catch (Exception e) {
                    log.error("Error during status polling for lab session {}: {}", labSessionId, e.getMessage());
                    try {
                        session.sendMessage(new TextMessage("\r\n❌ Error checking lab status.\r\n"));
                        session.close(CloseStatus.SERVER_ERROR);
                    } catch (IOException ioEx) {
                        log.warn("Could not send error message: {}", ioEx.getMessage());
                    }
                    break;
                }
            }
            
            // Timeout after max polls
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage("\r\n⏰ Timeout waiting for lab to be ready. Please refresh and try again.\r\n"));
                    session.close(CloseStatus.NORMAL.withReason("Timeout"));
                } catch (IOException e) {
                    log.warn("Could not send timeout message: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * Establish SSH connection for a lab that is confirmed to be READY
     */
    private void establishSSHConnectionForReadyLab(WebSocketSession session, UserLabSession userLabSession, String wsSessionId) {
        try {
            String vmName = "vm-" + userLabSession.getId();
            String namespace = userLabSession.getLab().getNamespace();

            log.info("Found VM details - Name: {}, Namespace: {}", vmName, namespace);
            
            // Get SSH connection details with retry
            SshConnectionDetails details = getSSHDetailsWithRetry(vmName, namespace, 5, 2000);

            // Establish SSH connection
            establishSSHConnection(session, details, wsSessionId);

        } catch (Exception e) {
            log.error("🚨 Failed to establish SSH connection for ready lab session {}: {}", userLabSession.getId(), e.getMessage(), e);
            try {
                session.sendMessage(new TextMessage("\r\n🚨 Error: Could not connect to the lab terminal. Details: " + e.getMessage()));
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ioEx) {
                log.warn("Could not send error message to client: {}", ioEx.getMessage());
            }
            cleanup(wsSessionId);
        }
    }

    /**
     * Retry logic để đợi SSH service sẵn sàng
     */
    private SshConnectionDetails getSSHDetailsWithRetry(String vmName, String namespace, int maxRetries, long delayMs) throws Exception {
        Exception lastException = null;
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                log.info("Attempt {} to get SSH details for VM: {}", i + 1, vmName);
                return discoveryService.getExternalSshDetails(vmName, namespace);
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {} failed: {}. Retrying in {} ms...", i + 1, e.getMessage(), delayMs);
                
                if (i < maxRetries - 1) { // Không sleep ở lần thử cuối
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for SSH service", ie);
                    }
                }
            }
        }
        
        throw new RuntimeException("SSH service not available after " + maxRetries + " attempts", lastException);
    }

    /**
     * Thiết lập kết nối SSH
     */
    private void establishSSHConnection(WebSocketSession session, SshConnectionDetails details, String wsSessionId) throws Exception {
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

        // Send welcome message
        session.sendMessage(new TextMessage("\r\n🚀 Terminal connected successfully! Welcome to your lab environment.\r\n"));

        // 5. Nối kết luồng output từ SSH đến WebSocket client
        // Tạo một luồng riêng để đọc dữ liệu từ máy ảo và gửi cho client
        CompletableFuture.runAsync(() -> {
            try {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while (channel.isConnected() && (bytesRead = in.read(buffer)) != -1) {
                    session.sendMessage(new TextMessage(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8)));
                }
            } catch (Exception e) {
                log.warn("Error reading from SSH stream for session {}, closing connection.", wsSessionId, e);
            } finally {
                cleanup(wsSessionId);
            }
        });
        
        // 6. Lưu lại các đối tượng cần thiết để quản lý phiên
        sshSessions.put(wsSessionId, jschSession);
        sshChannels.put(wsSessionId, channel);
        sshOutputStreams.put(wsSessionId, out);
    }

    /**
     * Tạo thông điệp trạng thái dựa trên status của lab
     */
    private String getStatusMessage(String status) {
        return switch (status) {
            case "PENDING" -> "\r\n🔄 Lab is being created... Please wait.\r\n";
            case "STARTING" -> "\r\n⚙️ Lab virtual machine is starting up...\r\n";
            case "SETTING_UP" -> "\r\n🛠️ Lab environment is being set up... This may take a few minutes.\r\n";
            case "FAILED", "SETUP_FAILED" -> "\r\n❌ Lab setup failed. Please try again.\r\n";
            default -> "\r\n⏳ Lab is not ready yet (status: " + status + "). Please wait...\r\n";
        };
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
        log.info("🧹 Cleaning up session: {}", sessionId);

        try {
            if (sshOutputStreams.containsKey(sessionId)) sshOutputStreams.get(sessionId).close();
        } catch (Exception ignored) {}
        sshOutputStreams.remove(sessionId);

        if (sshChannels.containsKey(sessionId)) sshChannels.get(sessionId).disconnect();
        sshChannels.remove(sessionId);

        if (sshSessions.containsKey(sessionId)) sshSessions.get(sessionId).disconnect();
        sshSessions.remove(sessionId);

        // Đóng Socket Bridge
        try {
            ServerSocket ss = portForwardSockets.remove(sessionId);
            if (ss != null && !ss.isClosed()) {
                ss.close();
            }
        } catch (Exception ignored) {}

        log.info("✅ Cleanup complete for session: {}", sessionId);
    }
}