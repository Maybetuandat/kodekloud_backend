package com.example.cms_be.ultil;

import com.example.cms_be.dto.lab.LabTestResponse;
import com.example.cms_be.model.InstanceType;
import com.example.cms_be.model.Lab;
import com.example.cms_be.service.VMTestOrchestrationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class VMTestAsyncExecutor {

    private final VMTestOrchestrationService orchestrationService;
    private final PodLogWebSocketHandler webSocketHandler;

    @Async("taskExecutor")
    public void executeTestAsync(String testId, Lab lab, InstanceType instanceType, String testVmName, 
                                  String namespace, Integer timeoutSeconds,
                                  ConcurrentHashMap<String, LabTestResponse> activeTests) {
        try {
            log.info("===========================================");
            log.info(" [ASYNC] STARTING LAB TEST EXECUTION");
            log.info(" Thread: {}", Thread.currentThread().getName());
            log.info(" Test ID: {}", testId);
            log.info(" Lab ID: {}", lab.getId());
            log.info(" Test VM Name: {}", testVmName);
            log.info("===========================================");

            // ✅ BƯỚC 1: ĐỢI WEBSOCKET CONNECTION (30 giây)
            log.info("⏳ Step 1: Waiting for WebSocket client to connect...");
            updateTestStatus(testId, "WAITING_CONNECTION", activeTests);
            
            boolean wsConnected = webSocketHandler.waitForConnection(testVmName, 30);
            
            if (!wsConnected) {
                log.error("❌ WebSocket connection timeout for VM: {}", testVmName);
                updateTestStatus(testId, "FAILED", activeTests);
                return;
            }
            
            log.info("✅ WebSocket client connected successfully!");
            
            // ✅ Gửi message xác nhận đã kết nối
            webSocketHandler.broadcastLogToPod(testVmName, "connection",
                    "🔗 WebSocket connected successfully. Starting test...", 
                    Map.of("testId", testId));

            // Small delay để đảm bảo message được gửi
            Thread.sleep(500);

            // ✅ BƯỚC 2: CẬP NHẬT STATUS VÀ BẮT ĐẦU TEST
            updateTestStatus(testId, "RUNNING", activeTests);

            // Gửi start message
            webSocketHandler.broadcastLogToPod(testVmName, "start",
                    String.format("🚀 Starting test for lab: %s (ID: %d)", lab.getTitle(), lab.getId()),
                    Map.of("testId", testId, "labId", lab.getId(), "labTitle", lab.getTitle()));

            // ✅ BƯỚC 3: THỰC THI TEST WORKFLOW
            boolean success = orchestrationService.executeTestWorkflow(
                    lab,
                    testVmName,
                    namespace != null ? namespace : lab.getNamespace(),
                    timeoutSeconds != null ? timeoutSeconds : 1800, 
                    instanceType
            );

            // ✅ BƯỚC 4: CẬP NHẬT KẾT QUẢ CUỐI CÙNG
            if (success) {
                updateTestStatus(testId, "COMPLETED", activeTests);
                webSocketHandler.broadcastLogToPod(testVmName, "success",
                        "✅ Test completed successfully!",
                        Map.of("testId", testId, "status", "COMPLETED"));
            } else {
                updateTestStatus(testId, "FAILED", activeTests);
                webSocketHandler.broadcastLogToPod(testVmName, "error",
                        "❌ Test failed!",
                        Map.of("testId", testId, "status", "FAILED"));
            }

            log.info("✅ LAB TEST EXECUTION FINISHED - Success: {}", success);
            
        } catch (InterruptedException e) {
            log.error("❌ Test execution interrupted for {}: {}", testId, e.getMessage());
            Thread.currentThread().interrupt();
            updateTestStatus(testId, "FAILED", activeTests);
            webSocketHandler.broadcastLogToPod(testVmName, "error",
                    "❌ Test interrupted: " + e.getMessage(),
                    Map.of("testId", testId, "error", "Interrupted"));
                    
        } catch (Exception e) {
            log.error("❌ Error executing test {}: {}", testId, e.getMessage(), e);
            updateTestStatus(testId, "FAILED", activeTests);
            webSocketHandler.broadcastLogToPod(testVmName, "error",
                    "❌ Test execution error: " + e.getMessage(),
                    Map.of("testId", testId, "error", e.getMessage()));
                    
        } finally {
            // ✅ BƯỚC 5: DỌN DẸP SAU 60 GIÂY
            try {
                log.info("⏳ Keeping test {} in memory for 60 seconds before cleanup...", testId);
                Thread.sleep(60000);
                activeTests.remove(testId);
                log.info("🧹 Test {} removed from active tests", testId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void updateTestStatus(String testId, String status, ConcurrentHashMap<String, LabTestResponse> activeTests) {
        LabTestResponse response = activeTests.get(testId);
        if (response != null) {
            response.setStatus(status);
            log.info("📊 Test {} status updated to: {}", testId, status);
        }
    }
}