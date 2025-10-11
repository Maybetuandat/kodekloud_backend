package com.example.cms_be.handler;

import com.example.cms_be.service.KubernetesService;
import io.kubernetes.client.Exec;
import io.kubernetes.client.openapi.ApiClient;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TerminalHandler extends TextWebSocketHandler {

    private final KubernetesService kubernetesService;

    private final Map<String, Process> k8sProcesses = new ConcurrentHashMap<>();
    private final Map<String, OutputStream> processOutputStreams = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cleanedUp = new ConcurrentHashMap<>();

    public TerminalHandler(KubernetesService kubernetesService) {
        this.kubernetesService = kubernetesService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        final String POD_NAMESPACE = "default";
        final String POD_NAME = "alpine";

        System.out.printf("✅ Client connected: %s. Attaching to hardcoded pod %s/%s...%n",
                session.getId(), POD_NAMESPACE, POD_NAME);

        cleanedUp.put(session.getId(), new AtomicBoolean(false));

        try {
            // Lấy ApiClient từ service đã được cấu hình
            ApiClient client = this.kubernetesService.getApiClient();
            Exec exec = new Exec(client);

            final String[] command = new String[]{"/bin/sh", "-i"};

            // Kết nối exec trực tiếp với thông tin đã hardcode
            final Process process = exec.exec(POD_NAMESPACE, POD_NAME, command, true, true);
            System.out.println("✅ K8s exec process created for: " + session.getId());

            // Logic stream output giữ nguyên
            Thread outputReader = new Thread(() -> {
                try (InputStream in = process.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while (session.isOpen() && (bytesRead = in.read(buffer)) != -1) {
                        session.sendMessage(new TextMessage(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8)));
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    cleanup(session.getId());
                }
            });
            outputReader.start();

            k8sProcesses.put(session.getId(), process);
            processOutputStreams.put(session.getId(), process.getOutputStream());

        } catch (Exception e) {
            e.printStackTrace();
            try {
                session.sendMessage(new TextMessage("Error attaching to pod: " + e.getMessage()));
            } catch (IOException ioException) {
                // Ignore
            }
            cleanup(session.getId());
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Phương thức này giờ chỉ chịu trách nhiệm gửi input tới process
        try {
            OutputStream out = processOutputStreams.get(session.getId());
            if (out != null) {
                out.write(message.getPayload().getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException e) {
            cleanup(session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("❌ Client disconnected: " + session.getId() + " | Status: " + status);
        cleanup(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        System.err.println("🚨 Transport error for session " + session.getId() + ": " + exception.getMessage());
        cleanup(session.getId());
    }

    private void cleanup(String sessionId) {
        if (cleanedUp.get(sessionId) != null && cleanedUp.get(sessionId).getAndSet(true)) {
            return;
        }
        System.out.println("🧹 Cleaning up K8s exec session: " + sessionId);
        OutputStream out = processOutputStreams.remove(sessionId);
        if (out != null) {
            try { out.close(); } catch (IOException ignored) {}
        }
        Process process = k8sProcesses.remove(sessionId);
        if (process != null) {
            process.destroy();
        }
        cleanedUp.remove(sessionId);
        System.out.println("✅ Cleanup complete for session: " + sessionId);
    }
}