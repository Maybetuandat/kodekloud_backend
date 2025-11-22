package com.example.cms_be.service;

import com.example.cms_be.ultil.PodLogWebSocketHandler;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.kubernetes.client.PodLogs;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class VMTestLogStreamerService {

    private final ApiClient apiClient;
    private final CoreV1Api coreApi;
    private final PodLogWebSocketHandler webSocketHandler;

    // Track active streaming tasks
    private final ConcurrentHashMap<String, AtomicBoolean> activeStreamings = new ConcurrentHashMap<>();

    /**
     * Start streaming pod logs (virt-launcher logs)
     */
    @Async
    public void startPodLogStreaming(String vmName, String namespace) {
        String streamKey = vmName + "-pod-logs";
        AtomicBoolean shouldContinue = new AtomicBoolean(true);
        activeStreamings.put(streamKey, shouldContinue);

        log.info("Starting pod log streaming for VM: {}", vmName);

        try {
            // Wait a bit for pod to be created
            Thread.sleep(5000);

            // Find the virt-launcher pod
            String labelSelector = "app=" + vmName;
            var podList = coreApi.listNamespacedPod(namespace, null, null, null, null, labelSelector, 1, null, null, null, null);

            if (podList.getItems().isEmpty()) {
                log.warn("No pod found for VM: {}", vmName);
                webSocketHandler.broadcastLogToPod(vmName, "warning",
                        "⚠️ Virt-launcher pod not found yet", null);
                return;
            }

            V1Pod pod = podList.getItems().get(0);
            String podName = pod.getMetadata().getName();
            log.info("Found virt-launcher pod: {}", podName);

            webSocketHandler.broadcastLogToPod(vmName, "info",
                    "📜 Streaming virt-launcher logs from pod: " + podName, null);

            // Stream logs using PodLogs
            PodLogs podLogs = new PodLogs(apiClient);
            InputStream logStream = podLogs.streamNamespacedPodLog(pod);

            BufferedReader reader = new BufferedReader(new InputStreamReader(logStream));
            String line;

            while (shouldContinue.get() && (line = reader.readLine()) != null) {
                // Broadcast each log line
                webSocketHandler.broadcastLogToPod(vmName, "pod-log",
                        "[VM-Pod] " + line, null);
            }

            reader.close();
            log.info("Pod log streaming ended for: {}", vmName);

        } catch (Exception e) {
            log.error("Error streaming pod logs for {}: {}", vmName, e.getMessage());
            webSocketHandler.broadcastLogToPod(vmName, "warning",
                    "⚠️ Pod log streaming error: " + e.getMessage(), null);
        } finally {
            activeStreamings.remove(streamKey);
        }
    }

    /**
     * Start streaming Kubernetes events
     */
    @Async
    public void startEventStreaming(String vmName, String namespace) {
        String streamKey = vmName + "-events";
        AtomicBoolean shouldContinue = new AtomicBoolean(true);
        activeStreamings.put(streamKey, shouldContinue);

        log.info("Starting K8s event streaming for VM: {}", vmName);

        try {
            webSocketHandler.broadcastLogToPod(vmName, "info",
                    "📡 Monitoring Kubernetes events...", null);

            // Watch events related to this VM
            String fieldSelector = String.format("involvedObject.name=%s", vmName);

            while (shouldContinue.get()) {
                try {
                    CoreV1EventList eventList = coreApi.listNamespacedEvent(
                            namespace, null, null, null, fieldSelector, null, null, null, null, 30, null);

                    for (CoreV1Event event : eventList.getItems()) {
                        String eventType = event.getType();
                        String reason = event.getReason();
                        String message = event.getMessage();
                        
                        String logType = "Normal".equals(eventType) ? "event-info" : "event-warning";
                        
                        webSocketHandler.broadcastLogToPod(vmName, logType,
                                String.format("[K8s-Event] %s: %s", reason, message),
                                Map.of("reason", reason, "type", eventType));
                    }

                    Thread.sleep(5000); // Poll every 5 seconds

                } catch (ApiException e) {
                    if (e.getCode() == 404) {
                        // Resource might be deleted
                        break;
                    }
                    log.warn("Error fetching events: {}", e.getMessage());
                    Thread.sleep(5000);
                }
            }

            log.info("Event streaming ended for: {}", vmName);

        } catch (Exception e) {
            log.error("Error streaming events for {}: {}", vmName, e.getMessage());
        } finally {
            activeStreamings.remove(streamKey);
        }
    }

    /**
     * Stream cloud-init logs from VM via SSH
     */
    @Async
    public void streamCloudInitLogs(KubernetesDiscoveryService.SshConnectionDetails sshDetails, String vmName) {
        String streamKey = vmName + "-cloud-init";
        AtomicBoolean shouldContinue = new AtomicBoolean(true);
        activeStreamings.put(streamKey, shouldContinue);

        log.info("Streaming cloud-init logs for VM: {}", vmName);

        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;

        try {
            // Create SSH session
            session = jsch.getSession("ubuntu", sshDetails.host(), sshDetails.port());
            session.setPassword("1234");
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);

            log.info("SSH connected for cloud-init log streaming");

            // First check if cloud-init is complete
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("cloud-init status --wait");
            
            InputStream in = channel.getInputStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;

            webSocketHandler.broadcastLogToPod(vmName, "info",
                    "⏳ Waiting for cloud-init to complete...", null);

            while ((line = reader.readLine()) != null) {
                webSocketHandler.broadcastLogToPod(vmName, "cloud-init",
                        "[Cloud-Init] " + line, null);
            }

            channel.disconnect();

            // Now stream the cloud-init output log
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("cat /var/log/cloud-init-output.log");
            
            in = channel.getInputStream();
            channel.connect();

            reader = new BufferedReader(new InputStreamReader(in));

            webSocketHandler.broadcastLogToPod(vmName, "info",
                    "📜 Streaming cloud-init output log...", null);

            while ((line = reader.readLine()) != null && shouldContinue.get()) {
                webSocketHandler.broadcastLogToPod(vmName, "cloud-init-log",
                        "[Cloud-Init-Output] " + line, null);
            }

            log.info("Cloud-init log streaming completed for: {}", vmName);

        } catch (Exception e) {
            log.error("Error streaming cloud-init logs for {}: {}", vmName, e.getMessage());
            webSocketHandler.broadcastLogToPod(vmName, "warning",
                    "⚠️ Cloud-init log streaming error: " + e.getMessage(), null);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            activeStreamings.remove(streamKey);
        }
    }

    /**
     * Stop all streaming for a VM
     */
    public void stopAllStreaming(String vmName) {
        log.info("Stopping all log streaming for VM: {}", vmName);

        activeStreamings.forEach((key, shouldContinue) -> {
            if (key.startsWith(vmName + "-")) {
                shouldContinue.set(false);
                log.info("Stopped streaming: {}", key);
            }
        });

        // Give threads time to cleanup
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Remove all entries for this VM
        activeStreamings.keySet().removeIf(key -> key.startsWith(vmName + "-"));
    }
}