package com.example.cms_be.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;

import org.springframework.stereotype.Service;

import com.example.cms_be.model.SetupStep;
import com.example.cms_be.repository.SetupStepRepository;
import com.example.cms_be.ultil.PodLogWebSocketHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SetupExecutionService {

    private final SetupStepRepository setupStepRepository;
    private final PodLogWebSocketHandler webSocketHandler;
    private final KubernetesService kubernetesService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * Thực thi setup steps với enhanced error handling
     */
    public CompletableFuture<Boolean> executeSetupStepsForAdminTest(String labId, String podName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeSetupSteps(labId, podName);
            } catch (Exception e) {
                log.error("Error executing setup steps for lab {}: {}", labId, e.getMessage(), e);
                sendErrorLog(podName, "SYSTEM_ERROR", "💥 Failed to execute setup steps: " + e.getMessage());
                return false;
            }
        }, executorService);
    }

    private boolean executeSetupSteps(String labId, String podName) throws Exception {
        log.info("Starting setup execution for lab {} on pod {}", labId, podName);
        
        sendInfoLog(podName, "SETUP_START", "🚀 Starting setup execution for lab " + labId);

        List<SetupStep> setupSteps = setupStepRepository.findByLabIdOrderByStepOrder(labId);
        
        if (setupSteps.isEmpty()) {
            sendWarningLog(podName, "NO_STEPS", "⚠️ No setup steps found for lab " + labId);
            log.warn("No setup steps found for lab {}", labId);
            return true;
        }

        sendInfoLog(podName, "STEPS_FOUND", String.format("📋 Found %d setup steps to execute", setupSteps.size()));

        // Đợi pod sẵn sàng
        try {
            sendInfoLog(podName, "POD_WAIT", "⏳ Waiting for pod to be ready...");
            kubernetesService.waitForPodReady(podName);
            sendSuccessLog(podName, "POD_READY", "✅ Pod is ready!");
            sendInfoLog(podName, "INIT_WAIT", "🔄 Allowing containers to initialize...");
            Thread.sleep(3000);
        } catch (Exception e) {
            log.error("Pod {} is not ready: {}", podName, e.getMessage());
            sendErrorLog(podName, "POD_NOT_READY", "❌ Pod not ready: " + e.getMessage());
            return false;
        }

        boolean allStepsSuccessful = true;
        int completedSteps = 0;

        for (SetupStep step : setupSteps) {
            try {
                log.info("Executing step {}/{}: {}", step.getStepOrder(), setupSteps.size(), step.getTitle());
                
                sendInfoLog(podName, "STEP_START", 
                    String.format("🔄 Executing Step %d/%d: %s", 
                        step.getStepOrder(), setupSteps.size(), step.getTitle()));

                StepExecutionResult result = executeStepWithErrorDetails(step, podName);
                
                if (result.isSuccess()) {
                    completedSteps++;
                    sendSuccessLog(podName, "STEP_SUCCESS", 
                        String.format("✅ Step %d completed successfully: %s", 
                            step.getStepOrder(), step.getTitle()));
                    log.info("Step {} completed successfully", step.getStepOrder());
                } else {
                    // Send detailed error information
                    sendErrorLog(podName, "STEP_FAILED", 
                        String.format("❌ Step %d failed: %s", step.getStepOrder(), step.getTitle()));
                    
                    // Send error analysis
                    sendErrorAnalysis(podName, step, result);
                        
                    allStepsSuccessful = false;
                    log.error("Step {} failed: {}", step.getStepOrder(), step.getTitle());
                    
                    if (!Boolean.TRUE.equals(step.getContinueOnFailure())) {
                        sendErrorLog(podName, "EXECUTION_STOPPED", 
                            "🛑 Stopping execution due to step failure (continueOnFailure = false)");
                        log.info("Stopping execution at step {} due to failure", step.getStepOrder());
                        break;
                    } else {
                        sendWarningLog(podName, "CONTINUE_ON_FAILURE", 
                            "⚠️ Step failed but continuing execution (continueOnFailure = true)");
                        log.info("Continuing execution despite step {} failure", step.getStepOrder());
                    }
                }

            } catch (Exception e) {
                log.error("Exception executing step {}: {}", step.getId(), e.getMessage(), e);
                allStepsSuccessful = false;
                
                sendErrorLog(podName, "STEP_EXCEPTION", 
                    String.format("💥 Step %d failed with exception: %s", step.getStepOrder(), e.getMessage()));
                
                // Send detailed exception info
                sendExceptionDetails(podName, step, e);
                
                if (!Boolean.TRUE.equals(step.getContinueOnFailure())) {
                    sendErrorLog(podName, "EXECUTION_STOPPED", "🛑 Stopping execution due to exception");
                    log.info("Stopping execution due to exception at step {}", step.getStepOrder());
                    break;
                }
            }
        }

        if (allStepsSuccessful) {
            sendSuccessLog(podName, "SETUP_COMPLETE", 
                String.format("🎉 All %d setup steps completed successfully!", setupSteps.size()));
        } else {
            sendWarningLog(podName, "SETUP_PARTIAL", 
                String.format("⚠️ Setup execution completed with errors (%d/%d steps successful)", 
                    completedSteps, setupSteps.size()));
        }

        log.info("Setup execution completed for lab {} on pod {}. Success: {}, Completed: {}/{}", 
            labId, podName, allStepsSuccessful, completedSteps, setupSteps.size());
            
        return allStepsSuccessful;
    }

    /**
     * Thực thi step với chi tiết về lỗi
     */
    private StepExecutionResult executeStepWithErrorDetails(SetupStep step, String podName) throws Exception {
        log.info("Executing step {}: {} with command: {}", step.getStepOrder(), step.getTitle(), step.getSetupCommand());
        
        int retryCount = step.getRetryCount() != null ? step.getRetryCount() : 1;
        int timeoutSeconds = step.getTimeoutSeconds() != null ? step.getTimeoutSeconds() : 300;
        Integer expectedExitCode = step.getExpectedExitCode() != null ? step.getExpectedExitCode() : 0;

        sendInfoLog(podName, "STEP_CONFIG", 
            String.format("⚙️ Step config - Expected exit: %d, Timeout: %ds, Retries: %d", 
                expectedExitCode, timeoutSeconds, retryCount));

        List<String> errorMessages = new ArrayList<>();
        List<String> stderrLines = new ArrayList<>();
        
        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                if (attempt > 1) {
                    sendWarningLog(podName, "RETRY_ATTEMPT", 
                        String.format("🔄 Retry attempt %d/%d for step %d", attempt, retryCount, step.getStepOrder()));
                    log.info("Retry attempt {}/{} for step {}", attempt, retryCount, step.getStepOrder());
                    Thread.sleep(2000);
                }

                sendInfoLog(podName, "COMMAND_EXECUTE", 
                    String.format("💻 Executing command: %s", step.getSetupCommand()));

                // Enhanced CommandOutputHandler để collect errors
                KubernetesService.CommandOutputHandler outputHandler = new KubernetesService.CommandOutputHandler() {
                    @Override
                    public void onStdout(String line) {
                        sendCommandLog(podName, "STDOUT", line, false);
                    }

                    @Override
                    public void onStderr(String line) {
                        stderrLines.add(line);
                        sendCommandLog(podName, "STDERR", line, true);
                    }
                };

                Integer exitCode = kubernetesService.executeCommandInPod(podName, step.getSetupCommand(), 
                    timeoutSeconds, outputHandler);
                
                sendInfoLog(podName, "EXIT_CODE", 
                    String.format("📤 Exit code: %d (expected: %d)", exitCode, expectedExitCode));
                log.info("Step {} attempt {} completed with exit code: {} (expected: {})", 
                    step.getStepOrder(), attempt, exitCode, expectedExitCode);
                
                if (exitCode.equals(expectedExitCode)) {
                    if (attempt > 1) {
                        sendSuccessLog(podName, "RETRY_SUCCESS", 
                            String.format("✅ Step succeeded on attempt %d", attempt));
                    }
                    return new StepExecutionResult(true, exitCode, attempt, new ArrayList<>(), new ArrayList<>());
                }
                
                // Collect error for this attempt
                String attemptError = String.format("Attempt %d failed with exit code %d", attempt, exitCode);
                errorMessages.add(attemptError);
                
                if (attempt < retryCount) {
                    sendWarningLog(podName, "ATTEMPT_FAILED", 
                        String.format("⚠️ Attempt %d failed with exit code %d, retrying...", attempt, exitCode));
                    log.warn("Step {} attempt {} failed with exit code: {}, retrying...", 
                        step.getStepOrder(), attempt, exitCode);
                } else {
                    sendErrorLog(podName, "ALL_ATTEMPTS_FAILED", 
                        String.format("❌ All %d attempts failed. Final exit code: %d", retryCount, exitCode));
                    log.error("Step {} failed after {} attempts. Final exit code: {}", 
                        step.getStepOrder(), retryCount, exitCode);
                }

            } catch (Exception e) {
                String attemptError = String.format("Attempt %d failed with exception: %s", attempt, e.getMessage());
                errorMessages.add(attemptError);
                
                log.error("Exception in step {} execution attempt {}: {}", step.getStepOrder(), attempt, e.getMessage());
                sendErrorLog(podName, "ATTEMPT_EXCEPTION", 
                    String.format("💥 Attempt %d failed with exception: %s", attempt, e.getMessage()));
                    
                if (attempt >= retryCount) {
                    throw e;
                }
            }
        }

        return new StepExecutionResult(false, -1, retryCount, errorMessages, stderrLines);
    }

    /**
     * Gửi phân tích lỗi chi tiết
     */
    private void sendErrorAnalysis(String podName, SetupStep step, StepExecutionResult result) {
        sendErrorLog(podName, "ERROR_ANALYSIS", "🔍 Error Analysis for Step " + step.getStepOrder() + ":");
        
        // Command that failed
        sendErrorLog(podName, "FAILED_COMMAND", "📋 Failed command: " + step.getSetupCommand());
        
        // Error messages from all attempts
        if (!result.getErrorMessages().isEmpty()) {
            sendErrorLog(podName, "ATTEMPT_ERRORS", "🚫 Attempt errors:");
            for (String error : result.getErrorMessages()) {
                sendErrorLog(podName, "ATTEMPT_ERROR_DETAIL", "  • " + error);
            }
        }
        
        // Stderr output analysis
        if (!result.getStderrLines().isEmpty()) {
            sendErrorLog(podName, "STDERR_ANALYSIS", "🔍 Error output analysis:");
            
            // Look for common error patterns
            analyzeCommonErrors(podName, result.getStderrLines());
            
            // Show last few stderr lines
            sendErrorLog(podName, "RECENT_STDERR", "📝 Recent error output:");
            int start = Math.max(0, result.getStderrLines().size() - 5);
            for (int i = start; i < result.getStderrLines().size(); i++) {
                sendErrorLog(podName, "STDERR_LINE", "  " + result.getStderrLines().get(i));
            }
        }
        
        // Troubleshooting suggestions
        sendTroubleshootingSuggestions(podName, step, result);
    }

    /**
     * Phân tích lỗi thường gặp
     */
    private void analyzeCommonErrors(String podName, List<String> stderrLines) {
        String allErrors = String.join(" ", stderrLines).toLowerCase();
        
        if (allErrors.contains("permission denied")) {
            sendErrorLog(podName, "ERROR_PATTERN", "🔒 Permission issue detected - check file/directory permissions");
        }
        if (allErrors.contains("no such file or directory")) {
            sendErrorLog(podName, "ERROR_PATTERN", "📂 File not found - check if path exists or file is installed");
        }
        if (allErrors.contains("command not found")) {
            sendErrorLog(podName, "ERROR_PATTERN", "⚡ Command not found - package may not be installed");
        }
        if (allErrors.contains("connection refused") || allErrors.contains("network unreachable")) {
            sendErrorLog(podName, "ERROR_PATTERN", "🌐 Network connectivity issue detected");
        }
        if (allErrors.contains("disk") && allErrors.contains("space")) {
            sendErrorLog(podName, "ERROR_PATTERN", "💾 Disk space issue detected");
        }
        if (allErrors.contains("timeout")) {
            sendErrorLog(podName, "ERROR_PATTERN", "⏰ Operation timeout - consider increasing timeout value");
        }
        if (allErrors.contains("already exists") || allErrors.contains("conflict")) {
            sendErrorLog(podName, "ERROR_PATTERN", "🔄 Resource conflict - item may already exist");
        }
    }

    /**
     * Gửi gợi ý khắc phục
     */
    private void sendTroubleshootingSuggestions(String podName, SetupStep step, StepExecutionResult result) {
        sendWarningLog(podName, "TROUBLESHOOTING", "💡 Troubleshooting suggestions:");
        
        sendWarningLog(podName, "SUGGESTION_1", "  1. Check if all required dependencies are installed");
        sendWarningLog(podName, "SUGGESTION_2", "  2. Verify network connectivity if downloading packages");
        sendWarningLog(podName, "SUGGESTION_3", "  3. Check disk space availability");
        sendWarningLog(podName, "SUGGESTION_4", "  4. Ensure proper permissions for files and directories");
        
        if (step.getTimeoutSeconds() != null && step.getTimeoutSeconds() < 600) {
            sendWarningLog(podName, "SUGGESTION_TIMEOUT", "  5. Consider increasing timeout (current: " + step.getTimeoutSeconds() + "s)");
        }
        
        if (step.getRetryCount() == null || step.getRetryCount() < 3) {
            sendWarningLog(podName, "SUGGESTION_RETRY", "  6. Consider increasing retry count for transient failures");
        }
        
        sendWarningLog(podName, "MANUAL_DEBUG", "  7. Try running the command manually in the pod for detailed debugging");
    }

    /**
     * Gửi chi tiết exception
     */
    private void sendExceptionDetails(String podName, SetupStep step, Exception e) {
        sendErrorLog(podName, "EXCEPTION_DETAILS", "🐛 Exception Details:");
        sendErrorLog(podName, "EXCEPTION_TYPE", "  Type: " + e.getClass().getSimpleName());
        sendErrorLog(podName, "EXCEPTION_MESSAGE", "  Message: " + e.getMessage());
        
        if (e.getCause() != null) {
            sendErrorLog(podName, "EXCEPTION_CAUSE", "  Cause: " + e.getCause().getMessage());
        }
        
        // Stack trace (first few lines)
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace.length > 0) {
            sendErrorLog(podName, "EXCEPTION_STACK", "  Stack trace (top 3 lines):");
            for (int i = 0; i < Math.min(3, stackTrace.length); i++) {
                sendErrorLog(podName, "STACK_LINE", "    " + stackTrace[i].toString());
            }
        }
    }

    // ============ LOGGING METHODS ============

    private void sendInfoLog(String podName, String logCode, String message) {
        LogMessage logMsg = new LogMessage("INFO", logCode, message, System.currentTimeMillis());
        webSocketHandler.broadcastLogToPod(podName, "log", message, logMsg);
    }

    private void sendSuccessLog(String podName, String logCode, String message) {
        LogMessage logMsg = new LogMessage("SUCCESS", logCode, message, System.currentTimeMillis());
        webSocketHandler.broadcastLogToPod(podName, "log", message, logMsg);
    }

    private void sendWarningLog(String podName, String logCode, String message) {
        LogMessage logMsg = new LogMessage("WARNING", logCode, message, System.currentTimeMillis());
        webSocketHandler.broadcastLogToPod(podName, "log", message, logMsg);
    }

    private void sendErrorLog(String podName, String logCode, String message) {
        LogMessage logMsg = new LogMessage("ERROR", logCode, message, System.currentTimeMillis());
        webSocketHandler.broadcastLogToPod(podName, "log", message, logMsg);
    }

    private void sendCommandLog(String podName, String logCode, String message, boolean isError) {
        String logLevel = isError ? "ERROR" : "INFO";
        LogMessage logMsg = new LogMessage(logLevel, logCode, message, System.currentTimeMillis());
        webSocketHandler.broadcastLogToPod(podName, "log", message, logMsg);
    }

    // ============ HELPER CLASSES ============

    public static class LogMessage {
        public final String level;
        public final String code;
        public final String message;
        public final long timestamp;
        public final String color;

        public LogMessage(String level, String code, String message, long timestamp) {
            this.level = level;
            this.code = code;
            this.message = message;
            this.timestamp = timestamp;
            this.color = getColorForLevel(level);
        }

        private String getColorForLevel(String level) {
            switch (level) {
                case "SUCCESS": return "#28a745";
                case "WARNING": return "#ffc107";
                case "ERROR": return "#dc3545";
                case "INFO":
                default: return "#6c757d";
            }
        }

        // Getters
        public String getLevel() { return level; }
        public String getCode() { return code; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
        public String getColor() { return color; }
    }

    /**
     * Kết quả thực thi step với thông tin chi tiết
     */
    public static class StepExecutionResult {
        private final boolean success;
        private final int exitCode;
        private final int attemptCount;
        private final List<String> errorMessages;
        private final List<String> stderrLines;

        public StepExecutionResult(boolean success, int exitCode, int attemptCount, 
                                 List<String> errorMessages, List<String> stderrLines) {
            this.success = success;
            this.exitCode = exitCode;
            this.attemptCount = attemptCount;
            this.errorMessages = new ArrayList<>(errorMessages);
            this.stderrLines = new ArrayList<>(stderrLines);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public int getExitCode() { return exitCode; }
        public int getAttemptCount() { return attemptCount; }
        public List<String> getErrorMessages() { return errorMessages; }
        public List<String> getStderrLines() { return stderrLines; }
    }
}