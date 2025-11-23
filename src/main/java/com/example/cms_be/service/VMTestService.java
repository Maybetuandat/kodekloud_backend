package com.example.cms_be.service;
import com.example.cms_be.dto.lab.LabTestResponse;
import com.example.cms_be.model.Lab;
import com.example.cms_be.repository.LabRepository;
import com.example.cms_be.ultil.PodLogWebSocketHandler;
import com.example.cms_be.ultil.SocketConnectionInfo;
import com.example.cms_be.ultil.VMTestAsyncExecutor;

import io.kubernetes.client.openapi.models.V1Pod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
@Service
@Slf4j
@RequiredArgsConstructor
public class VMTestService {

    private final LabRepository labRepository;
    private final VMTestAsyncExecutor asyncExecutor; // ← INJECT BEAN MỚI
    private final PodLogWebSocketHandler webSocketHandler;
    private final SocketConnectionInfo socketConnectionInfo;

    private final ConcurrentHashMap<String, LabTestResponse> activeTests = new ConcurrentHashMap<>();

   public LabTestResponse startLabTest(Integer labId) {
    log.info("[SYNC] Starting lab test for labId: {}", labId);

    Lab lab = labRepository.findById(labId)
            .orElseThrow(() -> new EntityNotFoundException("Lab not found with ID: " + labId));

    // use for force loading instancetype 
    if (lab.getInstanceType() != null) {
        lab.getInstanceType().getId(); 
        lab.getInstanceType().getStorageGb(); 
        lab.getInstanceType().getMemoryGb();
        lab.getInstanceType().getCpuCores();
        
    }

    // String testId = UUID.randomUUID().toString();
    // String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
    // String testVmName = String.format("test-vm-%d-%s", lab.getId(), timestamp);

    String testId = "1";
    String testVmName = "test-vm-1";
    log.info("Test ID: {}, Test VM Name: {}", testId, testVmName);

    Map<String, Object> connectionInfo = socketConnectionInfo.createWebSocketConnectionInfo(testVmName);
    String wsUrl = (String) connectionInfo.get("url");

    LabTestResponse response = new LabTestResponse(
            testId,
            lab.getId(),
            testVmName,
            wsUrl,
            connectionInfo
    );

    activeTests.put(testId, response);

    log.info(" Calling asyncExecutor.executeTestAsync()");
    asyncExecutor.executeTestAsync(testId, lab, lab.getInstanceType(), testVmName, lab.getNamespace(), 1800, activeTests);
    

    return response;
}

    

    public LabTestResponse getTestStatus(String testId) {
        LabTestResponse response = activeTests.get(testId);
        if (response == null) {
            throw new EntityNotFoundException("Test not found with ID: " + testId);
        }
        return response;
    }

    public void cancelTest(String testId) {
        LabTestResponse response = activeTests.get(testId);
        if (response != null) {
            response.setStatus("CANCELLED");
            webSocketHandler.broadcastLogToPod(response.getTestVmName(), "warning",
                    "⚠️ Test cancelled by user",
                    Map.of("testId", testId));
        }
    }
}