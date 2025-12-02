package com.example.cms_be.service;

import com.example.cms_be.dto.connection.SshConnectionDetails;
import com.example.cms_be.model.Lab;
import com.example.cms_be.model.SetupStep;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.UserLabSessionRepository;
import com.example.cms_be.repository.LabRepository;

import io.kubernetes.client.openapi.models.V1Pod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LabOrchestrationService {

    private final VMService vmService;
    private final KubernetesDiscoveryService discoveryService;
    private final SetupExecutionService setupExecutionService;
    private final UserLabSessionRepository userLabSessionRepository;
    private final LabRepository labRepository;

    @Value("${app.execution-environment:outside-cluster}")
    private String executionEnvironment;

   
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
        
        
        UserLabSession detachedSession = createDetachedSession(session, labWithSetupSteps);
        
        
        provisionAndSetupLabAsync(detachedSession);
    }

    /**
     * Create a detached session object with all required data loaded
     */
    private UserLabSession createDetachedSession(UserLabSession original, Lab loadedLab) {
        UserLabSession detached = new UserLabSession();
        detached.setId(original.getId());
        detached.setStatus(original.getStatus());
        detached.setCreatedAt(original.getCreatedAt());
        detached.setSetupStartedAt(original.getSetupStartedAt());
        detached.setSetupCompletedAt(original.getSetupCompletedAt());
        detached.setExpiresAt(original.getExpiresAt());
        detached.setPodName(original.getPodName());
        detached.setCourseUser(original.getCourseUser());
        
        
        detached.setLab(loadedLab);
        
        return detached;
    }

    @Async
    public void provisionAndSetupLabAsync(UserLabSession session) {
        String vmName = "vm-" + session.getId();
        String namespace = session.getLab().getNamespace();

        try {
            // === Phase 1: Create VM resources ===
            log.info("Phase 1: Creating VM resources for session {}...", session.getId());
            updateSessionStatus(session.getId(), "PENDING");
            vmService.createKubernetesResourcesForSession(session);

            // === Phase 2: Wait for VM to be ready ===
            log.info("Phase 2: Waiting for VM to be ready for session {}...", session.getId());
            updateSessionStatus(session.getId(), "STARTING");
            V1Pod pod = discoveryService.waitForPodRunning(vmName, namespace, 1200);

            // === Phase 3: Wait for SSH ready ===
            // 🔥 THAY ĐỔI: Không dùng discoveryService.waitForSshReady (NodePort) nữa.
            // SetupExecutionService sẽ tự retry kết nối qua tunnel.
            log.info("Phase 3: VM Pod is running. Skipping external SSH check (Tunneling mode).");

            // === Phase 4: Execute setup steps ===
            List<SetupStep> setupSteps = session.getLab().getSetupSteps();
            if (setupSteps != null && !setupSteps.isEmpty()) {
                log.info("Phase 4: Starting setup execution for session {}...", session.getId());
                updateSessionStatus(session.getId(), "SETTING_UP");
                updateSessionSetupCompletedTime(session.getId());

                // 🔥 GỌI HÀM MỚI (không truyền connectionDetails)
                setupExecutionService.executeSteps(session);
            } else {
                log.info("Phase 4: No setup steps. Marking as READY.");
                updateSessionStatus(session.getId(), "READY");
                updateSessionSetupCompletedTime(session.getId());
            }

            log.info("Lab orchestration completed for session {}.", session.getId());

        } catch (Exception e) {
            log.error("Error during orchestration: {}", e.getMessage(), e);
            updateSessionStatus(session.getId(), "SETUP_FAILED");
        }
    }

    @Async
    public void cleanupLabResources(UserLabSession session) {
        String vmName = "vm-" + session.getId();
        log.info("Phase 5: Cleaning up Kubernetes resources for session {} (VM: {})...", session.getId(), vmName);
        try {
            vmService.deleteKubernetesResourcesForSession(session);
            log.info("Successfully cleaned up resources for session {}.", session.getId());
        } catch (Exception e) {
            log.error("Error during background cleanup for session {}: {}", session.getId(), e.getMessage(), e);
        }
    }

    private void updateSessionStatus(Integer sessionId, String status) {
        userLabSessionRepository.findById(sessionId).ifPresent(session -> {
            log.info("Orchestrator: Updating session {} status from '{}' to '{}'.", 
                    sessionId, session.getStatus(), status);
            session.setStatus(status);
            userLabSessionRepository.save(session);
        });
    }
    
    private void updateSessionSetupCompletedTime(Integer sessionId) {
        userLabSessionRepository.findById(sessionId).ifPresent(session -> {
            log.info("Orchestrator: Setting setupCompletedAt time for session {}.", sessionId);
            session.setSetupCompletedAt(LocalDateTime.now());
            userLabSessionRepository.save(session);
        });
    }
}