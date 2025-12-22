package com.example.cms_be.service;


import com.example.cms_be.dto.lab.InstanceTypeDTO;
import com.example.cms_be.dto.lab.UserLabSessionRequest;
import com.example.cms_be.kafka.UserLabSessionProducer;
import com.example.cms_be.model.Lab;
import com.example.cms_be.model.SetupStep;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.LabRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LabOrchestrationService {

    private final LabRepository labRepository;
    private final UserLabSessionProducer userLabSessionProducer;
    private final ObjectMapper objectMapper;
    
    @Value("${infrastructure.service.websocket.student-url}")
    private String infrastructureWebSocketUrl;

    @Transactional(readOnly = true)
    public void provisionAndSetupLabWithEagerLoading(UserLabSession session) {
        log.info("Preparing data for async execution - session {}...", session.getId());
        
        Lab labWithSetupSteps = labRepository.findByIdWithAllData(session.getLab().getId())
            .orElseThrow(() -> new RuntimeException("Lab not found: " + session.getLab().getId()));
        
        List<SetupStep> setupSteps = labWithSetupSteps.getSetupSteps();
        if (setupSteps != null) {
            log.info("Loaded {} setup steps for lab {}", setupSteps.size(), labWithSetupSteps.getId());
        } else {
            log.info("No setup steps found for lab {}", labWithSetupSteps.getId());
        }
        
        try {
            String setupStepsJson = setupSteps != null && !setupSteps.isEmpty() 
                ? objectMapper.writeValueAsString(setupSteps) 
                : null;
            
            String vmName = "vm-" + session.getId();
            String wsUrl = String.format("%s?podName=%s", infrastructureWebSocketUrl, vmName);
            
            InstanceTypeDTO instanceTypeDTO = new InstanceTypeDTO(
                labWithSetupSteps.getInstanceType().getBackingImage(),
                labWithSetupSteps.getInstanceType().getCpuCores(),
                labWithSetupSteps.getInstanceType().getMemoryGb(),
                labWithSetupSteps.getInstanceType().getStorageGb()
            );
            
            UserLabSessionRequest request = UserLabSessionRequest.builder()
                .labSessionId(session.getId())
                .vmName(vmName)
                .namespace(labWithSetupSteps.getNamespace())
                .labId(labWithSetupSteps.getId())
                .instanceType(instanceTypeDTO)
                .setupStepsJson(setupStepsJson)
                .build();
            
            log.info("Sending user lab session request to Kafka for session {}", session.getId());
            log.info("VM Name: {}, WebSocket URL: {}", vmName, wsUrl);
            
            userLabSessionProducer.sendUserLabSessionRequest(request);
            
        } catch (Exception e) {
            log.error("Error preparing user lab session request: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send user lab session request", e);
        }
    }
}