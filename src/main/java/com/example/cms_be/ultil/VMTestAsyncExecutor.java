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
    public void executeTestAsync(String testId, Lab lab, InstanceType instanceType, String testVmName, String namespace, Integer timeoutSeconds,
                                  ConcurrentHashMap<String, LabTestResponse> activeTests) {
        try {
            log.info("===========================================");
            log.info(" [ASYNC] STARTING LAB TEST EXECUTION");
            log.info(" Thread: {}", Thread.currentThread().getName());
            log.info(" Test ID: {}", testId);
            log.info(" Lab ID: {}", lab.getId());
            log.info(" Test VM Name: {}", testVmName);
            log.info("===========================================");

            // Update status
            updateTestStatus(testId, "RUNNING", activeTests);

            // Send start message
            webSocketHandler.broadcastLogToPod(testVmName, "start",
                    String.format(" Starting test for lab: %s (ID: %d)", lab.getTitle(), lab.getId()),
                    Map.of("testId", testId, "labId", lab.getId(), "labTitle", lab.getTitle()));

            // Execute test workflow
            boolean success = orchestrationService.executeTestWorkflow(
                    lab,
                    testVmName,
                    namespace != null ? namespace : lab.getNamespace(),
                    timeoutSeconds != null ? timeoutSeconds : 1800, 
                    instanceType
            );

            // Update final status
            if (success) {
                updateTestStatus(testId, "COMPLETED", activeTests);
                webSocketHandler.broadcastLogToPod(testVmName, "success",
                        " Test completed successfully!",
                        Map.of("testId", testId, "status", "COMPLETED"));
            } else {
                updateTestStatus(testId, "FAILED", activeTests);
                webSocketHandler.broadcastLogToPod(testVmName, "error",
                        " Test failed!",
                        Map.of("testId", testId, "status", "FAILED"));
            }

            log.info(" LAB TEST EXECUTION FINISHED - Success: {}", success);
        } catch (Exception e) {
            log.error(" Error executing test {}: {}", testId, e.getMessage(), e);
            updateTestStatus(testId, "FAILED", activeTests);
            webSocketHandler.broadcastLogToPod(testVmName, "error",
                    " Test execution error: " + e.getMessage(),
                    Map.of("testId", testId, "error", e.getMessage()));
        } finally {
            try {
                Thread.sleep(60000);
                activeTests.remove(testId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void updateTestStatus(String testId, String status, ConcurrentHashMap<String, LabTestResponse> activeTests) {
        LabTestResponse response = activeTests.get(testId);
        if (response != null) {
            response.setStatus(status);
            log.info("Test {} status updated to: {}", testId, status);
        }
    }
}