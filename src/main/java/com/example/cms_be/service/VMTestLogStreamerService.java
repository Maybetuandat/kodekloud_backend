package com.example.cms_be.service;

import com.example.cms_be.dto.connection.SshConnectionDetails;
import com.example.cms_be.ultil.PodLogWebSocketHandler;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class VMTestLogStreamerService {

    private final PodLogWebSocketHandler webSocketHandler;

    
    private final ConcurrentHashMap<String, AtomicBoolean> activeStreamings = new ConcurrentHashMap<>();

    
    public void streamCloudInitLogs(SshConnectionDetails sshDetails, String vmName) {
        String streamKey = vmName + "-cloud-init";
        AtomicBoolean shouldContinue = new AtomicBoolean(true);
        activeStreamings.put(streamKey, shouldContinue);

        log.info("Streaming cloud-init logs for VM: {}", vmName);

        JSch jsch = new JSch();
        Session session = null;

        try {
            // Create SSH session
            session = jsch.getSession("ubuntu", sshDetails.host(), sshDetails.port());
            session.setPassword("1234");
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);

            log.info("SSH connected for cloud-init log streaming");

            // ===== STEP 1: Wait for cloud-init =====
            webSocketHandler.broadcastLogToPod(vmName, "info",
                    "⏳ Waiting for cloud-init to complete...", null);

            ChannelExec waitChannel = (ChannelExec) session.openChannel("exec");
            waitChannel.setCommand("cloud-init status --wait");
            
            InputStream waitStream = waitChannel.getInputStream();
            waitChannel.connect();

            BufferedReader waitReader = new BufferedReader(new InputStreamReader(waitStream));
            String line;
            
            while ((line = waitReader.readLine()) != null) {
                log.info("Cloud-init status: {}", line);
                webSocketHandler.broadcastLogToPod(vmName, "cloud-init-status",
                        "[Status] " + line, null);
            }
            
            waitChannel.disconnect();
            
            webSocketHandler.broadcastLogToPod(vmName, "success",
                    "✅ Cloud-init finished", null);

            // Delay nhỏ để đảm bảo log file được flush
            Thread.sleep(2000);

            // ===== STEP 2: Read cloud-init log file =====
            webSocketHandler.broadcastLogToPod(vmName, "info",
                    "📜 Fetching cloud-init output log...", null);

            ChannelExec logChannel = (ChannelExec) session.openChannel("exec");
            logChannel.setCommand("cat /var/log/cloud-init-output.log 2>&1 || echo '[Log file not found]'");
            
            InputStream logStream = logChannel.getInputStream();
            logChannel.connect();

            BufferedReader logReader = new BufferedReader(new InputStreamReader(logStream));
            int lineCount = 0;
            
            while ((line = logReader.readLine()) != null && shouldContinue.get()) {
                lineCount++;
                webSocketHandler.broadcastLogToPod(vmName, "cloud-init-log", line, null);
                
                // Throttle để tránh quá tải WebSocket
                if (lineCount % 50 == 0) {
                    Thread.sleep(10);
                }
            }

            logChannel.disconnect();

            webSocketHandler.broadcastLogToPod(vmName, "success",
                    String.format("✅ Cloud-init log complete (%d lines)", lineCount), null);

            log.info("Cloud-init log streaming completed: {} ({} lines)", vmName, lineCount);

        } catch (Exception e) {
            log.error("Error streaming cloud-init logs for {}: {}", vmName, e.getMessage(), e);
            webSocketHandler.broadcastLogToPod(vmName, "error",
                    "❌ Cloud-init log error: " + e.getMessage(), null);
                    
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            activeStreamings.remove(streamKey);
        }
    }
}