package com.example.cms_be.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.cms_be.model.SetupStep;
import com.example.cms_be.repository.SetupStepRepository;
import com.example.cms_be.ultil.PodLogWebSocketHandler;

import io.kubernetes.client.Exec;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SetupExecutionService {

    private final SetupStepRepository setupStepRepository;
    private final PodLogWebSocketHandler webSocketHandler;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Value("${kubernetes.namespace:default}")
    private String namespace;

    /**
     * Thực thi setup steps cho Admin test (realtime WebSocket, không lưu DB)
     */
    public CompletableFuture<Boolean> executeSetupStepsForAdminTest(String labId, String podName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeSetupSteps(labId, podName);
            } catch (Exception e) {
                log.error("Error executing setup steps for lab {}: {}", labId, e.getMessage(), e);
                webSocketHandler.broadcastLogToPod(podName, "error", 
                    "Failed to execute setup steps: " + e.getMessage(), null);
                return false;
            }
        }, executorService);
    }

    /**
     * Thực thi setup steps đồng bộ với improved logging
     */
    private boolean executeSetupSteps(String labId, String podName) throws Exception {
        log.info("Starting setup execution for lab {} on pod {}", labId, podName);
        
        // Gửi thông báo bắt đầu
        webSocketHandler.broadcastLogToPod(podName, "start", 
            "🚀 Starting setup execution for lab " + labId, null);

        // Lấy danh sách setup steps theo thứ tự
        List<SetupStep> setupSteps = setupStepRepository.findByLabIdOrderByStepOrder(labId);
        
        if (setupSteps.isEmpty()) {
            webSocketHandler.broadcastLogToPod(podName, "warning", 
                "⚠️ No setup steps found for lab " + labId, null);
            log.warn("No setup steps found for lab {}", labId);
            return true; // Không có steps cũng coi như thành công
        }

        webSocketHandler.broadcastLogToPod(podName, "info", 
            String.format("📋 Found %d setup steps to execute", setupSteps.size()), null);

        // Đợi pod sẵn sàng với improved logging
        try {
            waitForPodReady(podName);
        } catch (Exception e) {
            log.error("Pod {} is not ready: {}", podName, e.getMessage());
            webSocketHandler.broadcastLogToPod(podName, "error", 
                "❌ Pod not ready: " + e.getMessage(), null);
            return false;
        }

        boolean allStepsSuccessful = true;
        int completedSteps = 0;

        // Thực thi từng step theo thứ tự
        for (SetupStep step : setupSteps) {
            try {
                log.info("Executing step {}/{}: {}", step.getStepOrder(), setupSteps.size(), step.getTitle());
                
                webSocketHandler.broadcastLogToPod(podName, "step", 
                    String.format("🔨 Executing Step %d/%d: %s", 
                        step.getStepOrder(), setupSteps.size(), step.getTitle()), 
                    createStepData(step));

                boolean stepSuccess = executeStep(step, podName);
                
                if (stepSuccess) {
                    completedSteps++;
                    webSocketHandler.broadcastLogToPod(podName, "step_success", 
                        String.format("✅ Step %d completed successfully: %s", 
                            step.getStepOrder(), step.getTitle()), 
                        createStepData(step));
                    log.info("Step {} completed successfully", step.getStepOrder());
                } else {
                    webSocketHandler.broadcastLogToPod(podName, "step_error", 
                        String.format("❌ Step %d failed: %s", step.getStepOrder(), step.getTitle()), 
                        createStepData(step));
                        
                    allStepsSuccessful = false;
                    log.error("Step {} failed: {}", step.getStepOrder(), step.getTitle());
                    
                    if (!Boolean.TRUE.equals(step.getContinueOnFailure())) {
                        webSocketHandler.broadcastLogToPod(podName, "error", 
                            "🛑 Stopping execution due to step failure (continueOnFailure = false)", null);
                        log.info("Stopping execution at step {} due to failure", step.getStepOrder());
                        break;
                    } else {
                        webSocketHandler.broadcastLogToPod(podName, "warning", 
                            "⚠️ Step failed but continuing execution (continueOnFailure = true)", null);
                        log.info("Continuing execution despite step {} failure", step.getStepOrder());
                    }
                }

            } catch (Exception e) {
                log.error("Exception executing step {}: {}", step.getId(), e.getMessage(), e);
                allStepsSuccessful = false;
                
                webSocketHandler.broadcastLogToPod(podName, "error", 
                    String.format("💥 Step %d failed with exception: %s", step.getStepOrder(), e.getMessage()), 
                    createStepData(step));
                
                if (!Boolean.TRUE.equals(step.getContinueOnFailure())) {
                    webSocketHandler.broadcastLogToPod(podName, "error", 
                        "🛑 Stopping execution due to exception", null);
                    log.info("Stopping execution due to exception at step {}", step.getStepOrder());
                    break;
                }
            }
        }

        // Gửi thông báo kết thúc với detailed summary
        String finalMessage = allStepsSuccessful ? 
            String.format("🎉 All %d setup steps completed successfully!", setupSteps.size()) : 
            String.format("⚠️ Setup execution completed with errors (%d/%d steps successful)", 
                completedSteps, setupSteps.size());
            
        webSocketHandler.broadcastLogToPod(podName, allStepsSuccessful ? "success" : "warning", 
            finalMessage, createExecutionSummary(setupSteps.size(), completedSteps, allStepsSuccessful));

        log.info("Setup execution completed for lab {} on pod {}. Success: {}, Completed: {}/{}", 
            labId, podName, allStepsSuccessful, completedSteps, setupSteps.size());
            
        return allStepsSuccessful;
    }

    /**
     * Thực thi một setup step cụ thể với improved error handling
     */
    private boolean executeStep(SetupStep step, String podName) throws Exception {
        log.info("Executing step {}: {} with command: {}", step.getStepOrder(), step.getTitle(), step.getSetupCommand());
        
        int retryCount = step.getRetryCount() != null ? step.getRetryCount() : 1;
        int timeoutSeconds = step.getTimeoutSeconds() != null ? step.getTimeoutSeconds() : 300;
        Integer expectedExitCode = step.getExpectedExitCode() != null ? step.getExpectedExitCode() : 0;

        webSocketHandler.broadcastLogToPod(podName, "info", 
            String.format("⚙️ Step config - Expected exit: %d, Timeout: %ds, Retries: %d", 
                expectedExitCode, timeoutSeconds, retryCount), null);

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                if (attempt > 1) {
                    webSocketHandler.broadcastLogToPod(podName, "retry", 
                        String.format("🔄 Retry attempt %d/%d for step %d", attempt, retryCount, step.getStepOrder()), null);
                    log.info("Retry attempt {}/{} for step {}", attempt, retryCount, step.getStepOrder());
                    Thread.sleep(2000); // Wait 2 seconds before retry
                }

                // Stream command execution với realtime logs
                Integer exitCode = executeCommandWithLiveStream(podName, step.getSetupCommand(), timeoutSeconds);
                
                webSocketHandler.broadcastLogToPod(podName, "log", 
                    String.format("Exit code: %d (expected: %d)", exitCode, expectedExitCode), null);
                log.info("Step {} attempt {} completed with exit code: {} (expected: {})", 
                    step.getStepOrder(), attempt, exitCode, expectedExitCode);
                
                if (exitCode.equals(expectedExitCode)) {
                    if (attempt > 1) {
                        webSocketHandler.broadcastLogToPod(podName, "info", 
                            String.format("✅ Step succeeded on attempt %d", attempt), null);
                    }
                    return true; // Success
                }
                
                if (attempt < retryCount) {
                    webSocketHandler.broadcastLogToPod(podName, "warning", 
                        String.format("❌ Attempt %d failed with exit code %d, retrying...", attempt, exitCode), null);
                    log.warn("Step {} attempt {} failed with exit code: {}, retrying...", 
                        step.getStepOrder(), attempt, exitCode);
                } else {
                    webSocketHandler.broadcastLogToPod(podName, "error", 
                        String.format("❌ All %d attempts failed. Final exit code: %d", retryCount, exitCode), null);
                    log.error("Step {} failed after {} attempts. Final exit code: {}", 
                        step.getStepOrder(), retryCount, exitCode);
                }

            } catch (Exception e) {
                log.error("Exception in step {} execution attempt {}: {}", step.getStepOrder(), attempt, e.getMessage());
                webSocketHandler.broadcastLogToPod(podName, "error", 
                    String.format("💥 Attempt %d failed with exception: %s", attempt, e.getMessage()), null);
                    
                if (attempt >= retryCount) {
                    throw e;
                }
            }
        }

        return false; // All attempts failed
    }

    /**
     * Thực thi command với live streaming của output và improved error handling
     */
    private Integer executeCommandWithLiveStream(String podName, String command, int timeoutSeconds) throws Exception {
        ApiClient client = Configuration.getDefaultApiClient();
        Exec exec = new Exec();
        
        webSocketHandler.broadcastLogToPod(podName, "log", 
            String.format("📝 Executing command: %s", command), null);
        log.debug("Executing command in pod {}: {}", podName, command);

        try {
            String[] commandArray = {"/bin/bash", "-c", command};
            
            Process process = exec.exec(namespace, podName, commandArray, false, true);
            
            // Stream stdout in realtime
            CompletableFuture<Void> stdoutFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String logLine = line;
                        webSocketHandler.broadcastLogToPod(podName, "stdout", logLine, null);
                        log.debug("STDOUT: {}", logLine);
                    }
                } catch (IOException e) {
                    log.error("Error reading stdout from pod {}: {}", podName, e.getMessage());
                }
            }, executorService);

            // Stream stderr in realtime  
            CompletableFuture<Void> stderrFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String logLine = line;
                        webSocketHandler.broadcastLogToPod(podName, "stderr", logLine, null);
                        log.warn("STDERR: {}", logLine);
                    }
                } catch (IOException e) {
                    log.error("Error reading stderr from pod {}: {}", podName, e.getMessage());
                }
            }, executorService);

            // Wait for process completion with timeout
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            
            if (!completed) {
                process.destroyForcibly();
                webSocketHandler.broadcastLogToPod(podName, "error", 
                    String.format("⏰ Command timed out after %d seconds", timeoutSeconds), null);
                log.error("Command timed out after {} seconds in pod {}: {}", timeoutSeconds, podName, command);
                throw new Exception("Command execution timed out after " + timeoutSeconds + " seconds");
            }

            // Wait for stream readers to complete
            try {
                CompletableFuture.allOf(stdoutFuture, stderrFuture).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Stream readers did not complete within 5 seconds: {}", e.getMessage());
            }
            
            int exitCode = process.exitValue();
            log.debug("Command completed with exit code: {} in pod: {}", exitCode, podName);
            return exitCode;
            
        } catch (Exception e) {
            webSocketHandler.broadcastLogToPod(podName, "error", 
                "Error executing command: " + e.getMessage(), null);
            log.error("Error executing command in pod {}: {}", podName, e.getMessage());
            throw e;
        }
    }

    /**
     * Đợi pod sẵn sàng để thực thi commands với improved logging
     */
    private void waitForPodReady(String podName) throws Exception {
        webSocketHandler.broadcastLogToPod(podName, "info", "⏳ Waiting for pod to be ready...", null);
        log.info("Waiting for pod {} to be ready...", podName);
        
        CoreV1Api api = new CoreV1Api();
        
        for (int i = 0; i < 60; i++) { // Max 60 attempts (60 seconds)
            try {
                var pod = api.readNamespacedPod(podName, namespace, null);
                String phase = pod.getStatus().getPhase();
                
                log.debug("Pod {} status check {}/60: phase = {}", podName, i + 1, phase);
                
                if ("Running".equals(phase)) {
                    webSocketHandler.broadcastLogToPod(podName, "info", "✅ Pod is ready!", null);
                    log.info("Pod {} is ready and running", podName);
                    
                    // Additional check for container readiness
                    Thread.sleep(5000); // Wait 5 more seconds for containers to be fully ready
                    webSocketHandler.broadcastLogToPod(podName, "info", "🔄 Allowing containers to initialize...", null);
                    return;
                }
                
                if ("Failed".equals(phase) || "Succeeded".equals(phase)) {
                    String errorMsg = "Pod is in " + phase + " state";
                    log.error("Pod {} failed to start: {}", podName, errorMsg);
                    throw new Exception(errorMsg);
                }
                
                if (i % 10 == 0) { // Log every 10 seconds
                    webSocketHandler.broadcastLogToPod(podName, "info", 
                        String.format("⏳ Still waiting... Pod status: %s (%d/60)", phase, i + 1), null);
                }
                
                Thread.sleep(1000); // Wait 1 second
                
            } catch (Exception e) {
                if (i >= 59) { // Last attempt
                    log.error("Pod {} did not become ready in time: {}", podName, e.getMessage());
                    throw new Exception("Pod did not become ready in time: " + e.getMessage());
                }
                log.debug("Pod readiness check failed (attempt {}): {}", i + 1, e.getMessage());
            }
        }
        
        throw new Exception("Pod did not become ready within 60 seconds");
    }

    // Rest of the methods remain the same...
    private StepData createStepData(SetupStep step) {
        return new StepData(step);
    }

    public static class StepData {
        public final int stepOrder;
        public final String title;
        public final String command;
        public final String description;
        public final Integer expectedExitCode;
        public final Integer retryCount;
        public final Integer timeoutSeconds;
        public final Boolean continueOnFailure;

        public StepData(SetupStep step) {
            this.stepOrder = step.getStepOrder();
            this.title = step.getTitle();
            this.command = step.getSetupCommand();
            this.description = step.getDescription();
            this.expectedExitCode = step.getExpectedExitCode();
            this.retryCount = step.getRetryCount();
            this.timeoutSeconds = step.getTimeoutSeconds();
            this.continueOnFailure = step.getContinueOnFailure();
        }

        // Getters for JSON serialization
        public int getStepOrder() { return stepOrder; }
        public String getTitle() { return title; }
        public String getCommand() { return command; }
        public String getDescription() { return description; }
        public Integer getExpectedExitCode() { return expectedExitCode; }
        public Integer getRetryCount() { return retryCount; }
        public Integer getTimeoutSeconds() { return timeoutSeconds; }
        public Boolean getContinueOnFailure() { return continueOnFailure; }
    }

    private ExecutionSummary createExecutionSummary(int totalSteps, int completedSteps, boolean success) {
        return new ExecutionSummary(totalSteps, completedSteps, success);
    }

    public static class ExecutionSummary {
        public final int totalSteps;
        public final int completedSteps;
        public final boolean success;
        public final String status;

        public ExecutionSummary(int totalSteps, int completedSteps, boolean success) {
            this.totalSteps = totalSteps;
            this.completedSteps = completedSteps;
            this.success = success;
            this.status = success ? "COMPLETED" : "FAILED";
        }

        // Getters for JSON serialization
        public int getTotalSteps() { return totalSteps; }
        public int getCompletedSteps() { return completedSteps; }
        public boolean isSuccess() { return success; }
        public String getStatus() { return status; }
    }
}