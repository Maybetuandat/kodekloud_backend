package com.example.cms_be.service;

import com.example.cms_be.dto.connection.ExecuteCommandResult;
import com.example.cms_be.handler.LabTimerHandler;
import com.example.cms_be.model.Lab;
import com.example.cms_be.model.SetupStep;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.UserLabSessionRepository;
import com.jcraft.jsch.*;
import io.kubernetes.client.PortForward;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Pod;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SetupExecutionService {

    private final LabTimerHandler labTimerHandler;
    private final UserLabSessionRepository userLabSessionRepository;
    private static final Logger executionLogger = LoggerFactory.getLogger("executionLogger");
    private final ApiClient apiClient;
    private final KubernetesDiscoveryService discoveryService;

    @Value("${app.execution-environment}")
    private String executionEnvironment;

    @Getter
    @Value("${kubernetes.namespace:default}")
    private String namespace;

    private final String defaultUsername = "ubuntu";
    private final String defaultPassword = "1234";

    public SetupExecutionService(
            LabTimerHandler labTimerHandler,
            UserLabSessionRepository userLabSessionRepository,
            @Qualifier("longTimeoutApiClient") ApiClient apiClient,
            KubernetesDiscoveryService discoveryService) {
        this.labTimerHandler = labTimerHandler;
        this.userLabSessionRepository = userLabSessionRepository;
        this.apiClient = apiClient;
        this.discoveryService = discoveryService;
        this.apiClient.setReadTimeout(0); // Tắt timeout
    }

    @Async
    public void executeSteps(UserLabSession session) {
        log.info("Starting setup steps execution for session ID: {} via K8s SocketFactory", session.getId());

        JSch jsch = new JSch();
        Session sshSession = null;

        try {
            Lab lab = session.getLab();
            List<SetupStep> setupSteps = lab.getSetupSteps().stream()
                    .sorted(Comparator.comparing(SetupStep::getStepOrder))
                    .collect(Collectors.toList());

            if (setupSteps.isEmpty()) {
                updateSessionStatus(session, "READY");
                labTimerHandler.startTimerForSession(session);
                return;
            }

            // 1. Tìm Pod
            String vmName = "vm-" + session.getId();
            String namespace = lab.getNamespace();
            V1Pod pod = discoveryService.waitForPodRunning(vmName, namespace, 120);
            String podName = pod.getMetadata().getName();

            // 2. Kết nối SSH với Custom SocketFactory
            // Retry logic để chờ SSHD trong VM khởi động xong
            sshSession = connectSshWithRetry(jsch, namespace, podName, 20, 5000);

            log.info("[Session {}] SSH connected via K8s Tunnel. Executing steps...", session.getId());

            boolean overallSuccess = true;
            for (SetupStep step : setupSteps) {
                log.info("[Session {}] Executing: {}", session.getId(), step.getTitle());
                ExecuteCommandResult result = executeCommandOnSession(sshSession, step.getSetupCommand(), step.getTimeoutSeconds());
                logStepResult(session, step, result);

                if (result.getExitCode() != step.getExpectedExitCode()) {
                    if (!step.getContinueOnFailure()) {
                        overallSuccess = false;
                        break;
                    }
                }
            }

            updateSessionStatus(session, overallSuccess ? "READY" : "SETUP_FAILED");
            if (overallSuccess) labTimerHandler.startTimerForSession(session);

        } catch (Exception e) {
            log.error("Setup failed for session {}: {}", session.getId(), e.getMessage());
            updateSessionStatus(session, "SETUP_FAILED");
        } finally {
            if (sshSession != null && sshSession.isConnected()) {
                sshSession.disconnect();
            }
        }
    }

    /**
     * Hàm kết nối SSH sử dụng SocketFactory tùy chỉnh.
     * Mỗi lần retry sẽ tạo một Tunnel mới.
     */
    private Session connectSshWithRetry(JSch jsch, String namespace, String podName, int maxRetries, long delayMs) throws Exception {
        for (int i = 0; i < maxRetries; i++) {
            try {
                // Tạo Session JSch
                Session session = jsch.getSession(defaultUsername, "localhost", 2222); // Host/Port ảo
                session.setPassword(defaultPassword);
                session.setConfig("StrictHostKeyChecking", "no");

                // Gán SocketFactory để chặn việc tạo TCP Socket thật
                session.setSocketFactory(new K8sTunnelSocketFactory(apiClient, namespace, podName));

                // Kết nối (Timeout 15s)
                session.connect(15000);
                return session;

            } catch (JSchException e) {
                log.warn("SSH connect attempt {}/{} failed: {}. Retrying...", i + 1, maxRetries, e.getMessage());
                if (i == maxRetries - 1) throw e;
                Thread.sleep(delayMs);
            }
        }
        throw new RuntimeException("Failed to connect SSH after retries");
    }

    // --- Các hàm executeCommandOnSession, logStepResult, updateSessionStatus giữ nguyên như cũ ---
    private ExecuteCommandResult executeCommandOnSession(Session session, String command, int timeoutSeconds) throws Exception {
        ChannelExec channel = null;
        StringBuilder outputBuffer = new StringBuilder();
        int exitCode = -1;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();
            channel.connect(5000);

            byte[] buffer = new byte[1024];
            long startTime = System.currentTimeMillis();
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(buffer, 0, 1024);
                    if (i < 0) break;
                    outputBuffer.append(new String(buffer, 0, i));
                }
                while (err.available() > 0) {
                    int i = err.read(buffer, 0, 1024);
                    if (i < 0) break;
                    outputBuffer.append(new String(buffer, 0, i));
                }
                if (channel.isClosed()) {
                    if (in.available() > 0) continue;
                    exitCode = channel.getExitStatus();
                    break;
                }
                if (timeoutSeconds > 0 && (System.currentTimeMillis() - startTime) > timeoutSeconds * 1000L) {
                    throw new IOException("Command timeout");
                }
                Thread.sleep(100);
            }
        } finally {
            if (channel != null) channel.disconnect();
        }
        return new ExecuteCommandResult(exitCode, outputBuffer.toString().trim(), "");
    }

    private void logStepResult(UserLabSession session, SetupStep step, ExecuteCommandResult result) {
        if (result.getExitCode() == step.getExpectedExitCode()) {
            executionLogger.info("SESSION_ID={}|STEP='{}'|SUCCESS", session.getId(), step.getTitle());
        } else {
            executionLogger.error("SESSION_ID={}|STEP='{}'|FAILED|Code={}\nOUT: {}\nERR: {}",
                    session.getId(), step.getTitle(), result.getExitCode(), result.getStdout(), result.getStderr());
        }
    }

    private void updateSessionStatus(UserLabSession session, String status) {
        log.info("Updating session {} status to '{}'.", session.getId(), status);
        session.setStatus(status);
        userLabSessionRepository.save(session);
    }

    // =================================================================================
    // INNER CLASSES: K8sTunnelSocketFactory & VirtualSocket
    // =================================================================================

    /**
     * Factory này sẽ được JSch gọi khi session.connect().
     * Thay vì mở TCP Socket, nó mở K8s PortForward và trả về một VirtualSocket.
     */
    public static class K8sTunnelSocketFactory implements SocketFactory {
        private final ApiClient apiClient;
        private final String namespace;
        private final String podName;

        public K8sTunnelSocketFactory(ApiClient apiClient, String namespace, String podName) {
            this.apiClient = apiClient;
            this.namespace = namespace;
            this.podName = podName;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            try {
                // Tạo Tunnel MỚI mỗi khi JSch yêu cầu tạo Socket
                PortForward forward = new PortForward(apiClient);
                PortForward.PortForwardResult result = forward.forward(namespace, podName, Collections.singletonList(22));

                // Trả về Socket giả lập bao bọc Streams của K8s
                return new VirtualSocket(result.getInputStream(22), result.getOutboundStream(22));
            } catch (Exception e) {
                throw new IOException("Failed to create K8s tunnel: " + e.getMessage(), e);
            }
        }

        @Override
        public InputStream getInputStream(Socket socket) throws IOException {
            return socket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream(Socket socket) throws IOException {
            return socket.getOutputStream();
        }
    }

    /**
     * Một lớp Socket giả lập, chỉ dùng để chứa InputStream/OutputStream
     */
    public static class VirtualSocket extends Socket {
        private final InputStream in;
        private final OutputStream out;

        public VirtualSocket(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public InputStream getInputStream() {
            return in;
        }

        @Override
        public OutputStream getOutputStream() {
            return out;
        }

        @Override
        public boolean isConnected() {
            return true; // Luôn báo là đã kết nối
        }

        @Override
        public void close() throws IOException {
            if(in != null) in.close();
            if(out != null) out.close();
        }
    }
}