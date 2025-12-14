package com.example.cms_be.service;

import com.example.cms_be.dto.lab.InstanceTypeDTO;
import com.example.cms_be.dto.lab.LabTestRequest;
import com.example.cms_be.dto.lab.LabTestResponse;
import com.example.cms_be.kafka.LabTestRequestProducer;
import com.example.cms_be.model.Lab;
import com.example.cms_be.repository.LabRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VMTestService {

    private final LabRepository labRepository;
    private final LabTestRequestProducer labTestRequestProducer;

    @Value("${infrastructure.service.websocket.url}")
    private String infrastructureWebSocketUrl;

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

        String wsUrl = String.format("%s?podName=%s", infrastructureWebSocketUrl, testVmName);

        InstanceTypeDTO instanceTypeDTO = new InstanceTypeDTO(
            lab.getInstanceType().getBackingImage(),
            lab.getInstanceType().getCpuCores(),
            lab.getInstanceType().getMemoryGb(),
            lab.getInstanceType().getStorageGb()
        );

        LabTestRequest request = new LabTestRequest(
            lab.getId(),
            testVmName,
            lab.getNamespace(),
            lab.getTitle(),
            instanceTypeDTO
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

        log.info("Test request sent to infrastructure service. WebSocket URL: {}", wsUrl);
        return response;
    }
}