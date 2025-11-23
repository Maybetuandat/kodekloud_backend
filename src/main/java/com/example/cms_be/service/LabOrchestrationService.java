package com.example.cms_be.service;

import com.example.cms_be.dto.connection.SshConnectionDetails;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.UserLabSessionRepository;


import io.kubernetes.client.openapi.models.V1Pod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LabOrchestrationService {

    private final VMService vmService;
    private final KubernetesDiscoveryService discoveryService;
    private final SetupExecutionService setupExecutionService;
    private final UserLabSessionRepository userLabSessionRepository;

    @Value("${app.execution-environment:outside-cluster}")
    private String executionEnvironment;

    @Async
    public void provisionAndSetupLab(UserLabSession session) {
        String vmName = "vm-" + session.getId();
        String namespace = session.getLab().getNamespace();

        try {
            log.info("Phase 1: Creating VM resources for session {}...", session.getId());
            vmService.createKubernetesResourcesForSession(session);

            log.info("Phase 2: Waiting for VM to be ready for session {}...", session.getId());
            V1Pod pod = discoveryService.waitForPodRunning(vmName, namespace, 1200);

            SshConnectionDetails connectionDetails;
            if ("outside-cluster".equalsIgnoreCase(executionEnvironment)) {
                connectionDetails = discoveryService.getExternalSshDetails(vmName, namespace);
            } else {
                connectionDetails = discoveryService.getInternalSshDetails(pod);
            }
            discoveryService.waitForSshReady(connectionDetails.host(), connectionDetails.port(), 120);

            log.info("Phase 3: VM is ready. Starting setup execution for session {}.", session.getId());
            updateSessionStatus(session.getId(), "SETTING_UP");
            setupExecutionService.executeSteps(session, connectionDetails);

        } catch (Exception e) {
            log.error("A critical error occurred during lab orchestration for session {}: {}", session.getId(), e.getMessage(), e);
            updateSessionStatus(session.getId(), "FAILED");
        }
    }

    @Async
    public void cleanupLabResources(UserLabSession session) {
        String vmName = "vm-" + session.getId();
        log.info("Phase 4: Cleaning up Kubernetes resources for session {} (VM: {})...", session.getId(), vmName);
        try {
            vmService.deleteKubernetesResourcesForSession(session);
            log.info("Successfully cleaned up resources for session {}.", session.getId());
        } catch (Exception e) {
            log.error("Error during background cleanup for session {}: {}", session.getId(), e.getMessage(), e);
        }
    }

    private void updateSessionStatus(Integer sessionId, String status) {
        userLabSessionRepository.findById(sessionId).ifPresent(session -> {
            log.info("Orchestrator: Updating session {} status to '{}'.", sessionId, status);
            session.setStatus(status);
            userLabSessionRepository.save(session);
        });
    }
}