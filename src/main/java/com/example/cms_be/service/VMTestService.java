package com.example.cms_be.service;



import com.example.cms_be.dto.lab.LabTestRequest;
import com.example.cms_be.dto.lab.LabTestResponse;
import com.example.cms_be.model.Lab;
import com.example.cms_be.repository.LabRepository;
import com.example.cms_be.ultil.PodLogWebSocketHandler;
import com.example.cms_be.ultil.SocketConnectionInfo;
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

   public LabTestResponse startLabTest(LabTestRequest request) {
    log.info("▶️ [SYNC] Starting lab test for labId: {}", request.getLabId());

    Lab lab = labRepository.findById(request.getLabId())
            .orElseThrow(() -> new EntityNotFoundException("Lab not found with ID: " + request.getLabId()));

    // ===== CRITICAL: Force load lazy associations =====
    if (lab.getInstanceType() != null) {
        lab.getInstanceType().getId(); // Touch to initialize
        log.info("Loaded InstanceType: {}", lab.getInstanceType().getName());
    }

    String testId = UUID.randomUUID().toString();
    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
    String testVmName = String.format("test-vm-%d-%s", lab.getId(), timestamp);

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

    log.info("🚀 Calling asyncExecutor.executeTestAsync()");
    asyncExecutor.executeTestAsync(testId, lab, testVmName, request.getNamespace(), request.getTimeoutSeconds(), activeTests);
    log.info("✅ [SYNC] Response returned immediately!");

    return response;
}

    // XÓA method executeTestAsync() cũ đi - không cần nữa!

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