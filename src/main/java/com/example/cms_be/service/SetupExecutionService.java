package com.example.cms_be.service;

import java.io.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.example.cms_be.dto.connection.ExecuteCommandResult;
import com.example.cms_be.dto.connection.SshConnectionDetails;
import com.example.cms_be.handler.LabTimerHandler;
import com.example.cms_be.model.Lab;
import com.example.cms_be.model.SetupStep;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.UserLabSessionRepository;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SetupExecutionService {

    private final LabTimerHandler labTimerHandler;
    private final UserLabSessionRepository userLabSessionRepository;
    private static final Logger executionLogger = LoggerFactory.getLogger("executionLogger");

    @Value("${app.execution-environment}")
    private String executionEnvironment;

    @Getter
    @Value("${kubernetes.namespace:default}")
    private String namespace;

    @Value("${kubernetes.config.file.path:}")
    private String kubeConfigPath;

    private final String defaultUsername = "ubuntu";
    private final String defaultPassword = "1234";

   
    @Async
    public void executeSteps(UserLabSession session, SshConnectionDetails connectionDetails) {
        log.info("Starting setup steps execution for session ID: {}", session.getId());

        
        JSch jsch = new JSch();
        Session sshSession = null;

        try {
            Lab lab = session.getLab();
            List<SetupStep> setupSteps = lab.getSetupSteps().stream()
                    .sorted(Comparator.comparing(SetupStep::getStepOrder))
                    .collect(Collectors.toList());

            if (setupSteps.isEmpty()) {
                log.info("[Session {}] No setup steps found. Marking as READY.", session.getId());
                updateSessionStatus(session, "READY");
                labTimerHandler.startTimerForSession(session);
                return;
            }
            log.info("Opening SSH session to {} on port {}...", connectionDetails.host(), connectionDetails.port());
            sshSession = jsch.getSession(defaultUsername, connectionDetails.host(), connectionDetails.port());
            sshSession.setPassword(defaultPassword);
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.connect(150000); 

            boolean overallSuccess = true;
            log.info("[Session {}] Found {} setup steps to execute.", session.getId(), setupSteps.size());

    
            for (SetupStep step : setupSteps) {
                log.info("[Session {}] Executing step: '{}' (Order: {})", session.getId(), step.getTitle(), step.getStepOrder());
                
    
                ExecuteCommandResult result = executeCommandOnSession(sshSession, step.getSetupCommand(), step.getTimeoutSeconds());
                
                logStepResult(session, step, result);

                if (result.getExitCode() != step.getExpectedExitCode()) {
                    log.error("[Session {}] Step '{}' FAILED! (Expected: {}, Got: {})", session.getId(), step.getTitle(), step.getExpectedExitCode(), result.getExitCode());
                    if (!step.getContinueOnFailure()) {
                        log.warn("[Session {}] 'ContinueOnFailure' is false. Stopping execution.", session.getId());
                        overallSuccess = false;
                        break;
                    } else {
                        log.warn("[Session {}] 'ContinueOnFailure' is true. Continuing execution despite failure.", session.getId());
                    }
                } else {
                    log.info("[Session {}] Step '{}' successful.", session.getId(), step.getTitle());
                }
            }

            updateSessionStatus(session, overallSuccess ? "READY" : "SETUP_FAILED");

            if (overallSuccess) {
                log.info("[Session {}] LAB IS READY. Calling labTimerHandler.startTimerForSession().", session.getId());
                labTimerHandler.startTimerForSession(session);
            } else {
                log.warn("[Session {}] Lab setup failed.", session.getId());
            }

        } catch (Exception e) {
            log.error("A critical error occurred during setup execution for session {}: {}", session.getId(), e.getMessage(), e);
            updateSessionStatus(session, "SETUP_FAILED");
        } finally {
            // 3. Disconnect the Session only after ALL steps are done (or error occurred)
            if (sshSession != null && sshSession.isConnected()) {
                sshSession.disconnect();
                log.info("SSH session closed for session ID: {}", session.getId());
            }
        }
    }

    /**
     * Executes a command using an EXISTING SSH Session.
     */
    private ExecuteCommandResult executeCommandOnSession(Session session, String command, int timeoutSeconds) throws Exception {
        ChannelExec channel = null;
        StringBuilder outputBuffer = new StringBuilder();
        int exitCode = -1;

        try {
            // Open a new 'exec' channel on the existing session
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();

            channel.connect(5000); // Connect the channel (fast)
            log.debug("Executing command on existing channel: {}", command);

            byte[] buffer = new byte[1024];
            long startTime = System.currentTimeMillis();
            long timeoutMillis = timeoutSeconds * 1000L;

            while (true) {
                // Read STDOUT
                while (in.available() > 0) {
                    int i = in.read(buffer, 0, 1024);
                    if (i < 0) break;
                    outputBuffer.append(new String(buffer, 0, i));
                }
                // Read STDERR
                while (err.available() > 0) {
                    int i = err.read(buffer, 0, 1024);
                    if (i < 0) break;
                    outputBuffer.append(new String(buffer, 0, i));
                }

                if (channel.isClosed()) {
                    // Read any remaining output after close
                    while (in.available() > 0) {
                        int i = in.read(buffer, 0, 1024);
                        if (i < 0) break;
                        outputBuffer.append(new String(buffer, 0, i));
                    }
                    exitCode = channel.getExitStatus();
                    break;
                }

                // Timeout check
                if (timeoutSeconds > 0 && (System.currentTimeMillis() - startTime) > timeoutMillis) {
                    throw new IOException("Command execution timed out after " + timeoutSeconds + " seconds");
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            // Only disconnect the CHANNEL, keep the SESSION open
            if (channel != null) {
                channel.disconnect();
            }
        }
        return new ExecuteCommandResult(exitCode, outputBuffer.toString().trim(), "");
    }

    private void logStepResult(UserLabSession session, SetupStep step, ExecuteCommandResult result) {
        if (result.getExitCode() == step.getExpectedExitCode()) {
            executionLogger.info(
                    "SESSION_ID={}|STEP_ID={}|STEP_TITLE='{}'|STATUS=SUCCESS|EXIT_CODE={}\n--- STDOUT ---\n{}\n--- STDERR ---\n{}",
                    session.getId(), step.getId(), step.getTitle(), result.getExitCode(), result.getStdout(), result.getStderr()
            );
        } else {
            executionLogger.error(
                    "SESSION_ID={}|STEP_ID={}|STEP_TITLE='{}'|STATUS=FAILED|EXIT_CODE={}\n--- STDOUT ---\n{}\n--- STDERR ---\n{}",
                    session.getId(), step.getId(), step.getTitle(), result.getExitCode(), result.getStdout(), result.getStderr()
            );
        }
    }

    private void updateSessionStatus(UserLabSession session, String status) {
        log.info("Updating session {} status from '{}' to '{}'.", session.getId(), session.getStatus(), status);
        session.setStatus(status);
        userLabSessionRepository.save(session);
    }
}