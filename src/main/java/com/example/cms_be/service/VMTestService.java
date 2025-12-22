// cms-backend/src/main/java/com/example/cms_be/service/VMTestService.java
package com.example.cms_be.service;

import com.example.cms_be.dto.lab.InstanceTypeDTO;
import com.example.cms_be.dto.lab.LabTestRequest;
import com.example.cms_be.dto.lab.LabTestResponse;
import com.example.cms_be.kafka.LabTestRequestProducer;
import com.example.cms_be.model.Lab;
import com.example.cms_be.repository.LabRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class VMTestService {

    private final LabRepository labRepository;
    private final LabTestRequestProducer labTestRequestProducer;
    private final ObjectMapper objectMapper;

    @Value("${infrastructure.service.websocket.admin-test-url}")
    private String adminTestWebSocketUrl;

    public LabTestResponse startLabTest(Integer labId) {
        log.info("[SYNC] Starting lab test for labId: {}", labId);

        Lab lab = labRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Lab not found with ID: " + labId));

        if (lab.getInstanceType() != null) {
            lab.getInstanceType().getId();
            lab.getInstanceType().getStorageGb();
            lab.getInstanceType().getMemoryGb();
            lab.getInstanceType().getCpuCores();
        }

        String testId = UUID.randomUUID().toString();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String testVmName = String.format("test-vm-%d-%s", lab.getId(), timestamp);

        log.info("Test ID: {}, Test VM Name: {}", testId, testVmName);

        // Admin test WebSocket URL
        String wsUrl = String.format("%s?podName=%s", adminTestWebSocketUrl, testVmName);

        InstanceTypeDTO instanceTypeDTO = new InstanceTypeDTO(
            lab.getInstanceType().getBackingImage(),
            lab.getInstanceType().getCpuCores(),
            lab.getInstanceType().getMemoryGb(),
            lab.getInstanceType().getStorageGb()
        );

        // Convert setup steps to JSON
        String setupStepsJson = null;
        try {
            if (lab.getSetupSteps() != null && !lab.getSetupSteps().isEmpty()) {
                List<Map<String, Object>> setupStepsList = lab.getSetupSteps().stream()
                    .map(step -> {
                        Map<String, Object> stepMap = new HashMap<>();
                        stepMap.put("stepOrder", step.getStepOrder());
                        stepMap.put("title", step.getTitle());
                        stepMap.put("setupCommand", step.getSetupCommand());
                        stepMap.put("retryCount", step.getRetryCount());
                        stepMap.put("expectedExitCode", step.getExpectedExitCode());
                        stepMap.put("timeoutSeconds", step.getTimeoutSeconds());
                        stepMap.put("continueOnFailure", step.getContinueOnFailure());
                        return stepMap;
                    })
                    .collect(Collectors.toList());
                
                setupStepsJson = objectMapper.writeValueAsString(setupStepsList);
                log.info("Setup steps JSON: {}", setupStepsJson);
            }
        } catch (Exception e) {
            log.error("Error serializing setup steps: {}", e.getMessage(), e);
        }

        LabTestRequest request = new LabTestRequest(
            lab.getId(),
            testVmName,
            lab.getNamespace(),
            lab.getTitle(),
            instanceTypeDTO,
            setupStepsJson
        );
        
        labTestRequestProducer.sendLabTestRequest(request);

        LabTestResponse response = LabTestResponse.builder()
                .testId(testId)
                .labId(lab.getId())
                .testVmName(testVmName)
                .status("WAITING_CONNECTION")
                .websocketUrl(wsUrl)
                .connectionInfo(Map.of(
                    "url", wsUrl,
                    "podName", testVmName
                ))
                .build();

        log.info("Test request sent to infrastructure service. Admin Test WebSocket URL: {}", wsUrl);
        return response;
    }
}