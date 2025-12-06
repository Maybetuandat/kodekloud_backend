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

    private final KubernetesDiscoveryService discoveryService;
    private final UserLabSessionRepository userLabSessionRepository;
    private final ApiClient apiClient;

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

            V1Pod pod = discoveryService.waitForPodRunning(vmName, namespace, 10);
            String podName = pod.getMetadata().getName();
            log.info("[{}] Target Pod: {}", wsSessionId, podName);

            serverSocket = new ServerSocket(0);
            int localPort = serverSocket.getLocalPort();
            portForwardSockets.put(wsSessionId, serverSocket);

            log.info("[{}] Local Bridge Server started on port {}", wsSessionId, localPort);

            final ServerSocket bridgeServer = serverSocket;

            CompletableFuture.runAsync(() -> {
                try {
                    Socket jschSocket = bridgeServer.accept();

                    PortForward forward = new PortForward(apiClient);
                    PortForward.PortForwardResult result = forward.forward(namespace, podName, Collections.singletonList(22));

                    InputStream k8sIn = result.getInputStream(22);
                    OutputStream k8sOut = result.getOutboundStream(22);

                    InputStream jschIn = jschSocket.getInputStream();
                    OutputStream jschOut = jschSocket.getOutputStream();

                    log.info("[{}] Tunnel pipes connected. Bridging data...", wsSessionId);

                    CompletableFuture.runAsync(() -> {
                        try {
                            jschIn.transferTo(k8sOut);
                        } catch (IOException e) {
                        }
                    });

                    try {
                        k8sIn.transferTo(jschOut);
                    } catch (IOException e) {
                    }

                } catch (Exception e) {
                    log.error("[{}] Bridge error: {}", wsSessionId, e.getMessage());
                }
            });

            JSch jsch = new JSch();
            Session jschSession = jsch.getSession("ubuntu", "127.0.0.1", localPort);
            jschSession.setPassword("1234");
            jschSession.setConfig("StrictHostKeyChecking", "no");

            jschSession.connect(10000);

            log.info("[{}] JSch connected to Local Bridge successfully", wsSessionId);

            ChannelShell channel = (ChannelShell) jschSession.openChannel("shell");
            channel.setPty(true);

            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();
            channel.connect();

            sshSessions.put(wsSessionId, jschSession);
            sshChannels.put(wsSessionId, channel);
            sshOutputStreams.put(wsSessionId, out);

            wsSession.sendMessage(new TextMessage("\r\n🚀 Connected via K8s Secure Tunnel!\r\n"));

            CompletableFuture.runAsync(() -> {
                try {
                    byte[] buffer = new byte[4096];
                    int i;
                    while ((i = in.read(buffer)) != -1) {
                        wsSession.sendMessage(new TextMessage(new String(buffer, 0, i, StandardCharsets.UTF_8)));
                    }
                } catch (Exception e) {
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

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
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

        try {
            ServerSocket ss = portForwardSockets.remove(sessionId);
            if (ss != null && !ss.isClosed()) {
                ss.close();
            }
        } catch (Exception ignored) {}

        log.info("✅ Cleanup complete for session: {}", sessionId);
    }
}