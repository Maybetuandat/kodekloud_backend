package com.example.cms_be.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.cms_be.model.SetupStep;
import com.example.cms_be.repository.SetupStepRepository;
import com.example.cms_be.service.KubernetesService.CommandOutputHandler;
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

    /**
     * Thực thi setup steps cho Admin test - HOÀN TOÀN TUẦN TỰ với FULL BACKEND LOGGING
     */
    public boolean executeSetupStepsForAdminTest(Integer labId, String podName) {
        try {
            return executeSetupStepsSequentially(labId, podName);
        } catch (Exception e) {
            log.error("Error executing setup steps for lab {}: {}", labId, e.getMessage(), e);
            webSocketHandler.broadcastLogToPod(podName, "error", 
                "Failed to execute setup steps: " + e.getMessage(), null);
            return false;
        }
    }

    /**
     * Thực thi setup steps HOÀN TOÀN TUẦN TỰ với ENHANCED BACKEND LOGGING
     */
    private boolean executeSetupStepsSequentially(Integer labId, String podName) throws Exception {
        log.info("========================================");
        log.info("🔥 STARTING SEQUENTIAL SETUP EXECUTION");
        log.info("🏷️ LAB ID: {}", labId);
        log.info("🚀 POD NAME: {}", podName);
        log.info("========================================");
        
        webSocketHandler.broadcastLogToPod(podName, "start", 
            "🔄 Starting SEQUENTIAL setup execution for lab " + labId, null);

        // Lấy setup steps theo thứ tự
        List<SetupStep> setupSteps = setupStepRepository.findByLabIdOrderByStepOrder(labId);
        
        if (setupSteps.isEmpty()) {
            log.warn("❌ NO SETUP STEPS FOUND for lab {}", labId);
            webSocketHandler.broadcastLogToPod(podName, "warning", 
                "❌ No setup steps found for lab " + labId, null);
            return true;
        }

        log.info("📋 FOUND {} SETUP STEPS to execute sequentially", setupSteps.size());
        webSocketHandler.broadcastLogToPod(podName, "info", 
            String.format("📋 Found %d setup steps to execute SEQUENTIALLY", setupSteps.size()), null);

        // Hiển thị danh sách steps trước khi thực thi
        log.info("📝 SETUP STEPS OVERVIEW:");
        for (int i = 0; i < setupSteps.size(); i++) {
            SetupStep step = setupSteps.get(i);
            log.info("   Step {}: {} - Command: {}", 
                step.getStepOrder(), step.getTitle(), step.getSetupCommand());
        }
        log.info("========================================");

        // Đợi pod sẵn sàng
        try {
            log.info("⏳ WAITING FOR POD TO BE READY...");
            webSocketHandler.broadcastLogToPod(podName, "info", "⏳ Waiting for pod to be ready...", null);
            kubernetesService.waitForPodReady(podName);
            log.info("✅ POD IS READY!");
            webSocketHandler.broadcastLogToPod(podName, "info", "✅ Pod is ready!", null);
            webSocketHandler.broadcastLogToPod(podName, "info", "🔄 Allowing containers to initialize...", null);
        } catch (Exception e) {
            log.error("❌ POD NOT READY: {}", e.getMessage());
            webSocketHandler.broadcastLogToPod(podName, "error", 
                "❌ Pod not ready: " + e.getMessage(), null);
            return false;
        }

        boolean allStepsSuccessful = true;
        int completedSteps = 0;

        // ===== THỰC THI TUẦN TỰ TUYỆT ĐỐI =====
        for (int i = 0; i < setupSteps.size(); i++) {
            SetupStep step = setupSteps.get(i);
            
            try {
                log.info("========================================");
                log.info("🎯 EXECUTING STEP {}/{}", step.getStepOrder(), setupSteps.size());
                log.info("📝 TITLE: {}", step.getTitle());
                log.info("💻 COMMAND: {}", step.getSetupCommand());
                log.info("========================================");
                
                webSocketHandler.broadcastLogToPod(podName, "step", 
                    String.format("🔄 Executing Step %d/%d: %s", 
                        step.getStepOrder(), setupSteps.size(), step.getTitle()), 
                    createStepData(step));

                // THỰC THI STEP VÀ CHỜ HOÀN THÀNH HOÀN TOÀN
                boolean stepSuccess = executeStepSynchronously(step, podName);
                
                if (stepSuccess) {
                    completedSteps++;
                    log.info("✅ STEP {} COMPLETED SUCCESSFULLY: {}", step.getStepOrder(), step.getTitle());
                    webSocketHandler.broadcastLogToPod(podName, "step_success", 
                        String.format("✅ Step %d completed successfully: %s", 
                            step.getStepOrder(), step.getTitle()), 
                        createStepData(step));
                    
                    // DELAY BẮT BUỘC giữa các steps
                    if (i < setupSteps.size() - 1) {
                        log.info("⏱️ WAITING 3 seconds before next step for system stability...");
                        webSocketHandler.broadcastLogToPod(podName, "info", 
                            "⏱️ Waiting 3 seconds before next step to ensure system stability...", null);
                        Thread.sleep(3000);
                    }
                    
                } else {
                    log.error("❌ STEP {} FAILED: {}", step.getStepOrder(), step.getTitle());
                    webSocketHandler.broadcastLogToPod(podName, "step_error", 
                        String.format("❌ Step %d failed: %s", step.getStepOrder(), step.getTitle()), 
                        createStepData(step));
                        
                    allStepsSuccessful = false;
                    
                    if (!Boolean.TRUE.equals(step.getContinueOnFailure())) {
                        log.info("🛑 STOPPING EXECUTION due to step failure (continueOnFailure = false)");
                        webSocketHandler.broadcastLogToPod(podName, "error", 
                            "🛑 Stopping execution due to step failure (continueOnFailure = false)", null);
                        break;
                    } else {
                        log.info("⚠️ CONTINUING EXECUTION despite step failure (continueOnFailure = true)");
                        webSocketHandler.broadcastLogToPod(podName, "warning", 
                            "⚠️ Step failed but continuing execution (continueOnFailure = true)", null);
                        
                        if (i < setupSteps.size() - 1) {
                            log.info("⏱️ WAITING 3 seconds before next step (after failure)...");
                            webSocketHandler.broadcastLogToPod(podName, "info", 
                                "⏱️ Waiting 3 seconds before next step (after failure)...", null);
                            Thread.sleep(3000);
                        }
                    }
                }

            } catch (Exception e) {
                log.error("💥 EXCEPTION EXECUTING STEP {}: {}", step.getId(), e.getMessage(), e);
                allStepsSuccessful = false;
                
                webSocketHandler.broadcastLogToPod(podName, "error", 
                    String.format("💥 Step %d failed with exception: %s", step.getStepOrder(), e.getMessage()), 
                    createStepData(step));
                
                if (!Boolean.TRUE.equals(step.getContinueOnFailure())) {
                    log.info("🛑 STOPPING EXECUTION due to exception");
                    webSocketHandler.broadcastLogToPod(podName, "error", 
                        "🛑 Stopping execution due to exception", null);
                    break;
                } else {
                    log.info("⚠️ CONTINUING EXECUTION despite exception (continueOnFailure = true)");
                    webSocketHandler.broadcastLogToPod(podName, "warning", 
                        "⚠️ Exception occurred but continuing execution (continueOnFailure = true)", null);
                    
                    if (i < setupSteps.size() - 1) {
                        log.info("⏱️ WAITING 3 seconds before next step (after exception)...");
                        webSocketHandler.broadcastLogToPod(podName, "info", 
                            "⏱️ Waiting 3 seconds before next step (after exception)...", null);
                        Thread.sleep(3000);
                    }
                }
            }
        }

        // LOG KẾT QUẢ CUỐI CÙNG
        log.info("========================================");
        log.info("🏁 SEQUENTIAL SETUP EXECUTION COMPLETED");
        log.info("🏷️ LAB ID: {}", labId);
        log.info("🚀 POD NAME: {}", podName);
        log.info("✅ SUCCESS: {}", allStepsSuccessful);
        log.info("📊 COMPLETED STEPS: {}/{}", completedSteps, setupSteps.size());
        log.info("📈 SUCCESS RATE: {}%", setupSteps.size() > 0 ? (completedSteps * 100 / setupSteps.size()) : 100);
        log.info("========================================");

        // Gửi thông báo kết thúc
        String finalMessage = allStepsSuccessful ? 
            String.format("🎉 All %d setup steps completed successfully in SEQUENTIAL order!", setupSteps.size()) : 
            String.format("⚠️ Sequential setup execution completed with errors (%d/%d steps successful)", 
                completedSteps, setupSteps.size());
            
        webSocketHandler.broadcastLogToPod(podName, allStepsSuccessful ? "success" : "warning", 
            finalMessage, createExecutionSummary(setupSteps.size(), completedSteps, allStepsSuccessful));
            
        return allStepsSuccessful;
    }

    /**
     * Thực thi một setup step cụ thể HOÀN TOÀN ĐỒNG BỘ với FULL BACKEND LOGGING
     */
    private boolean executeStepSynchronously(SetupStep step, String podName) throws Exception {
        int retryCount = step.getRetryCount() != null ? step.getRetryCount() : 1;
        int timeoutSeconds = step.getTimeoutSeconds() != null ? step.getTimeoutSeconds() : 300;
        Integer expectedExitCode = step.getExpectedExitCode() != null ? step.getExpectedExitCode() : 0;

        log.info("⚙️ STEP CONFIGURATION:");
        log.info("   Expected Exit Code: {}", expectedExitCode);
        log.info("   Timeout: {} seconds", timeoutSeconds);
        log.info("   Retry Count: {}", retryCount);
        log.info("   Continue on Failure: {}", step.getContinueOnFailure());

        webSocketHandler.broadcastLogToPod(podName, "info", 
            String.format("⚙️ Step config - Expected exit: %d, Timeout: %ds, Retries: %d", 
                expectedExitCode, timeoutSeconds, retryCount), null);

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                if (attempt > 1) {
                    log.info("🔄 RETRY ATTEMPT {}/{} for step {}", attempt, retryCount, step.getStepOrder());
                    webSocketHandler.broadcastLogToPod(podName, "retry", 
                        String.format("🔄 Retry attempt %d/%d for step %d", attempt, retryCount, step.getStepOrder()), null);
                    
                    log.info("⏱️ WAITING 5 seconds before retry...");
                    webSocketHandler.broadcastLogToPod(podName, "info", 
                        "⏱️ Waiting 5 seconds before retry to ensure system cleanup...", null);
                    Thread.sleep(5000);
                }

                log.info("💻 EXECUTING COMMAND: {}", step.getSetupCommand());
                webSocketHandler.broadcastLogToPod(podName, "log", 
                    String.format("💻 Executing command: %s", step.getSetupCommand()), null);

                // Enhanced CommandOutputHandler với FULL BACKEND LOGGING
                CommandOutputHandler outputHandler = new CommandOutputHandler() {
                    private int stdoutLineNumber = 1;
                    private int stderrLineNumber = 1;
                    
                    @Override
                    public void onStdout(String line) {
                        // Backend logging đã được handle trong KubernetesService
                        // Chỉ gửi đến WebSocket cho frontend
                        webSocketHandler.broadcastLogToPod(podName, "stdout", line, null);
                    }

                    @Override
                    public void onStderr(String line) {
                        // Backend logging đã được handle trong KubernetesService
                        // Chỉ gửi đến WebSocket cho frontend
                        webSocketHandler.broadcastLogToPod(podName, "stderr", line, null);
                    }
                };

                log.info("🚀 [STEP-{}] STARTING COMMAND EXECUTION", step.getStepOrder());
                
                // SỬ DỤNG METHOD MỚI ĐỂ LẤY ĐẦY ĐỦ OUTPUT VÀ LOG BACKEND
                KubernetesService.CommandResult result = kubernetesService.executeCommandInPodWithOutput(
                    podName, step.getSetupCommand(), timeoutSeconds, outputHandler);
                
                // LOG KẾT QUẢ CHI TIẾT
                log.info("✅ [STEP-{}] COMMAND COMPLETED", step.getStepOrder());
                log.info("   Exit Code: {} (Expected: {})", result.getExitCode(), expectedExitCode);
                log.info("   STDOUT Length: {} characters", result.getStdout().length());
                log.info("   STDERR Length: {} characters", result.getStderr().length());
                log.info("   Total Output Length: {} characters", result.getCombinedOutput().length());
                
                // SEND SUMMARY TO WEBSOCKET
                webSocketHandler.broadcastLogToPod(podName, "output_summary", 
                    String.format("📋 Command completed - Exit: %d, Output: %d chars", 
                        result.getExitCode(), result.getCombinedOutput().length()), 
                    Map.of(
                        "exitCode", result.getExitCode(),
                        "expectedExitCode", expectedExitCode,
                        "stdoutLength", result.getStdout().length(),
                        "stderrLength", result.getStderr().length(),
                        "totalLength", result.getCombinedOutput().length(),
                        "attempt", attempt,
                        "maxAttempts", retryCount
                    ));

                webSocketHandler.broadcastLogToPod(podName, "log", 
                    String.format("📤 Exit code: %d (expected: %d)", result.getExitCode(), expectedExitCode), null);

                // Check exit code
                if (result.getExitCode() == expectedExitCode) {
                    log.info("✅ [STEP-{}] SUCCESS - Exit code matches expected value", step.getStepOrder());
                    webSocketHandler.broadcastLogToPod(podName, "info", 
                        "⏱️ Command completed successfully, waiting 1 second for cleanup...", null);
                    Thread.sleep(1000);
                    return true;
                } else {
                    log.warn("❌ [STEP-{}] FAILED - Exit code mismatch: got {}, expected {}", 
                        step.getStepOrder(), result.getExitCode(), expectedExitCode);
                    
                    webSocketHandler.broadcastLogToPod(podName, "warning", 
                        String.format("⚠️ Exit code mismatch: got %d, expected %d", result.getExitCode(), expectedExitCode), null);
                    
                    if (attempt == retryCount) {
                        log.error("❌ [STEP-{}] FINAL FAILURE after {} attempts", step.getStepOrder(), retryCount);
                        return false;
                    }
                    log.info("🔄 [STEP-{}] Will retry - attempt {}/{}", step.getStepOrder(), attempt, retryCount);
                }
                
            } catch (Exception e) {
                log.error("💥 [STEP-{}] EXCEPTION on attempt {}/{}: {}", 
                    step.getStepOrder(), attempt, retryCount, e.getMessage());
                webSocketHandler.broadcastLogToPod(podName, "error", 
                    String.format("⚠️ Attempt %d/%d failed: %s", attempt, retryCount, e.getMessage()), null);
                
                if (attempt == retryCount) {
                    log.error("❌ [STEP-{}] FINAL EXCEPTION after {} attempts", step.getStepOrder(), retryCount);
                    throw e;
                }
                log.info("🔄 [STEP-{}] Will retry after exception - attempt {}/{}", step.getStepOrder(), attempt, retryCount);
            }
        }
        
        return false;
    }

    /**
     * Tạo step data cho WebSocket
     */
    private Object createStepData(SetupStep step) {
        return Map.of(
            "stepId", step.getId(),
            "stepOrder", step.getStepOrder(),
            "title", step.getTitle(),
            "command", step.getSetupCommand(),
            "timeout", step.getTimeoutSeconds() != null ? step.getTimeoutSeconds() : 300,
            "retryCount", step.getRetryCount() != null ? step.getRetryCount() : 1,
            "continueOnFailure", step.getContinueOnFailure() != null ? step.getContinueOnFailure() : false,
            "expectedExitCode", step.getExpectedExitCode() != null ? step.getExpectedExitCode() : 0
        );
    }

    /**
     * Tạo execution summary
     */
    private Object createExecutionSummary(int totalSteps, int completedSteps, boolean allSuccessful) {
        return Map.of(
            "totalSteps", totalSteps,
            "completedSteps", completedSteps,
            "successRate", totalSteps > 0 ? (double) completedSteps / totalSteps * 100 : 0,
            "allSuccessful", allSuccessful,
            "executionMode", "SEQUENTIAL",
            "timestamp", java.time.LocalDateTime.now().toString()
        );
    }
}