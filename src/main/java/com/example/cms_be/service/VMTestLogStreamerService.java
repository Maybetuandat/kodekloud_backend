package com.example.cms_be.service;

import com.example.cms_be.ultil.PodLogWebSocketHandler;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.kubernetes.client.openapi.ApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class VMTestLogStreamerService {

    private final PodLogWebSocketHandler webSocketHandler;
    private final ApiClient apiClient; // Client timeout=0
    private final ConcurrentHashMap<String, AtomicBoolean> activeStreamings = new ConcurrentHashMap<>();

    private final String defaultUsername = "ubuntu";
    private final String defaultPassword = "1234";

    public VMTestLogStreamerService(
            PodLogWebSocketHandler webSocketHandler,
            @Qualifier("longTimeoutApiClient") ApiClient apiClient) {
        this.webSocketHandler = webSocketHandler;
        this.apiClient = apiClient;
        this.apiClient.setReadTimeout(0);
    }

    /**
     * Stream log Cloud-init qua Tunnel K8s
     */
    public void streamCloudInitLogs(String namespace, String podName, String testVmName) {
        String streamKey = testVmName + "-cloud-init";
        AtomicBoolean shouldContinue = new AtomicBoolean(true);
        activeStreamings.put(streamKey, shouldContinue);

        log.info("Streaming cloud-init logs for Test VM: {} (Pod: {})", testVmName, podName);

        JSch jsch = new JSch();
        Session session = null;

        try {
            // Retry Connection Logic (Tương tự SetupExecutionService)
            session = connectSshWithRetry(jsch, namespace, podName, 20, 5000); // 100s timeout tổng

            log.info("SSH connected via Tunnel for Cloud-init streaming");

            // ===== STEP 1: Wait for cloud-init =====
            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    "⏳ Waiting for cloud-init to complete...", null);

            // Channel 1: Check Status
            executeCommandAndStream(session, "cloud-init status --wait", testVmName, "cloud-init-status", shouldContinue);

            webSocketHandler.broadcastLogToPod(testVmName, "success",
                    "✅ Cloud-init finished", null);

            Thread.sleep(1000);

            // ===== STEP 2: Read cloud-init log file =====
            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    "📜 Fetching cloud-init output log...", null);

            // Channel 2: Read Log
            executeCommandAndStream(session, "cat /var/log/cloud-init-output.log 2>&1", testVmName, "cloud-init-log", shouldContinue);

            webSocketHandler.broadcastLogToPod(testVmName, "success",
                    "✅ Cloud-init log streaming complete", null);

        } catch (Exception e) {
            log.error("Error streaming cloud-init logs for {}: {}", testVmName, e.getMessage());
            webSocketHandler.broadcastLogToPod(testVmName, "error",
                    "❌ Cloud-init log error: " + e.getMessage(), null);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            activeStreamings.remove(streamKey);
        }
    }

    private void executeCommandAndStream(Session session, String command, String testVmName, String logType, AtomicBoolean shouldContinue) throws Exception {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect(5000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            int lineCount = 0;

            while ((line = reader.readLine()) != null && shouldContinue.get()) {
                lineCount++;
                webSocketHandler.broadcastLogToPod(testVmName, logType, line, null);
                if (lineCount % 20 == 0) Thread.sleep(10); // Throttle nhẹ
            }
        } finally {
            if (channel != null) channel.disconnect();
        }
    }

    private Session connectSshWithRetry(JSch jsch, String namespace, String podName, int maxRetries, long delayMs) throws Exception {
        for (int i = 0; i < maxRetries; i++) {
            try {
                Session session = jsch.getSession(defaultUsername, "localhost", 2222);
                session.setPassword(defaultPassword);
                session.setConfig("StrictHostKeyChecking", "no");

                // Sử dụng Factory chung từ SetupExecutionService
                session.setSocketFactory(new SetupExecutionService.K8sTunnelSocketFactory(apiClient, namespace, podName));

                session.connect(15000);
                return session;
            } catch (Exception e) {
                log.warn("SSH connect attempt {}/{} failed: {}", i + 1, maxRetries, e.getMessage());
                if (i == maxRetries - 1) throw e;
                Thread.sleep(delayMs);
            }
        }
        throw new RuntimeException("Failed to connect SSH after retries");
    }
}