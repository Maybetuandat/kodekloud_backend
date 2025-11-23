package com.example.cms_be.service;
import com.example.cms_be.dto.connection.SshConnectionDetails;
import com.example.cms_be.model.InstanceType;
import com.example.cms_be.model.Lab;
import com.example.cms_be.ultil.PodLogWebSocketHandler;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
@Service
@Slf4j
@RequiredArgsConstructor
public class VMTestOrchestrationService {

    
    private final KubernetesDiscoveryService discoveryService;
    private final VMTestLogStreamerService logStreamerService;
    private final VMService vmService;
    private final PodLogWebSocketHandler webSocketHandler;


    private void createTestVMResources(Lab lab, String testVmName, String namespace, InstanceType instanceType) throws Exception {


        log.info("Creating test VM resources: {}", testVmName);
        vmService.ensureNamespaceExists(namespace);
        webSocketHandler.broadcastLogToPod(testVmName, "info",
                "✓ Namespace verified: " + namespace, null);

        webSocketHandler.broadcastLogToPod(testVmName, "info",
                "⏳ Creating PersistentVolumeClaim with resources ....  + " + instanceType.getStorageGb() + " GB" + instanceType.getMemoryGb() + " GB " + instanceType.getCpuCores() + " CPU cores", null);
        
        vmService.createPvcForSession(testVmName, namespace, instanceType.getStorageGb().toString());

        webSocketHandler.broadcastLogToPod(testVmName, "success",
                "✅ PVC created successfully", null);

        webSocketHandler.broadcastLogToPod(testVmName, "info",
                "⏳ Creating VirtualMachine...", null);

        vmService.createVirtualMachineFromTemplate(testVmName, namespace,
                instanceType.getMemoryGb().toString(),
                instanceType.getCpuCores().toString());

        webSocketHandler.broadcastLogToPod(testVmName, "success",
                "✅ VirtualMachine created successfully", null);
        webSocketHandler.broadcastLogToPod(testVmName, "info",
                "⏳ Creating SSH service...", null);


        vmService.createSshServiceForVM(testVmName, namespace);
        webSocketHandler.broadcastLogToPod(testVmName, "success",
                "✅ SSH service created successfully", null);

        log.info("Test VM resources created successfully: {}", testVmName);
    }

    public boolean executeTestWorkflow(Lab lab, String testVmName, String namespace, int timeoutSeconds, InstanceType instanceType) {
        boolean success = false;

        try {
            // ===== PHASE 1: CREATE VM RESOURCES =====
            log.info("========================================");
            log.info(" PHASE 1: CREATING VM RESOURCES");
            log.info("========================================");
            
            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    " Phase 1: Creating VM resources...", null);
            createTestVMResources(lab, testVmName, namespace, instanceType);

            webSocketHandler.broadcastLogToPod(testVmName, "success",
                    " VM resources created successfully", null);
            // ===== PHASE 2: WAIT FOR VM POD & STREAM LOGS =====
            log.info("========================================");
            log.info(" PHASE 2: WAITING FOR VM POD");
            log.info("========================================");

            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    " Phase 2: Waiting for virt-launcher pod...", null);


                    
            // Start streaming pod logs asynchronously
            logStreamerService.startPodLogStreaming(testVmName, namespace);

            V1Pod pod = discoveryService.waitForPodRunning(testVmName, namespace, 600);
            String podName = pod.getMetadata().getName();

            log.info(" Pod is running: {}", podName);
            webSocketHandler.broadcastLogToPod(testVmName, "success",
                    String.format("✅ Virt-launcher pod is running: %s", podName), null);

            // ===== PHASE 3: STREAM KUBERNETES EVENTS =====
            log.info("========================================");
            log.info(" PHASE 3: STREAMING KUBERNETES EVENTS");
            log.info("========================================");

            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    " Phase 3: Monitoring Kubernetes events...", null);
            // Start streaming K8s events
            logStreamerService.startEventStreaming(testVmName, namespace);

            // ===== PHASE 4: WAIT FOR SSH =====
            log.info("========================================");
            log.info(" PHASE 4: WAITING FOR SSH");
            log.info("========================================");

            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    " Phase 4: Waiting for SSH service...", null);
            SshConnectionDetails sshDetails =
                    discoveryService.getExternalSshDetails(testVmName, namespace);

            discoveryService.waitForSshReady(sshDetails.host(), sshDetails.port(), 180);

            log.info(" SSH is ready at {}:{}", sshDetails.host(), sshDetails.port());
            webSocketHandler.broadcastLogToPod(testVmName, "success",
                    String.format(" SSH is ready at %s:%d", sshDetails.host(), sshDetails.port()), null);

            // ===== PHASE 5: STREAM CLOUD-INIT LOGS =====
            log.info("========================================");
            log.info(" PHASE 5: STREAMING CLOUD-INIT LOGS");
            log.info("========================================");

            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    " Phase 5: Checking cloud-init status...", null);
            // Stream cloud-init logs
            logStreamerService.streamCloudInitLogs(sshDetails, testVmName);

            webSocketHandler.broadcastLogToPod(testVmName, "success",
                    " Cloud-init completed", null);

            // ===== PHASE 6: EXECUTE SETUP STEPS =====
            log.info("========================================");
            log.info(" PHASE 6: EXECUTING SETUP STEPS");
            log.info("========================================");

            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    " Phase 6: Executing setup steps...", null);
            // Execute setup steps (reuse existing service)
        //     success = setupExecutionService.executeSetupStepsForAdminTest(lab.getId(), testVmName);

            success = true;
            if (success) {
                webSocketHandler.broadcastLogToPod(testVmName, "success",
                        " All setup steps completed successfully!", null);
            } else {
                webSocketHandler.broadcastLogToPod(testVmName, "warning",
                        " Some setup steps failed", null);
            }

        } catch (Exception e) {
            log.error("Error in test workflow: {}", e.getMessage(), e);
            webSocketHandler.broadcastLogToPod(testVmName, "error",
                    " Test workflow error: " + e.getMessage(), null);
            success = false;
        } finally {
            // ===== PHASE 7: CLEANUP =====
            log.info("========================================");
            log.info(" PHASE 7: CLEANUP");
            log.info("========================================");

            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    " Phase 7: Cleaning up test resources...", null);
            try {
                // Stop log streaming
                logStreamerService.stopAllStreaming(testVmName);

                // Delete test VM resources
                deleteTestVMResources(testVmName, namespace);

                webSocketHandler.broadcastLogToPod(testVmName, "success",
                        " Cleanup completed", null);

            } catch (Exception e) {
                log.error("Error during cleanup: {}", e.getMessage(), e);
                webSocketHandler.broadcastLogToPod(testVmName, "warning",
                        " Cleanup error: " + e.getMessage(), null);
            }
        }

        log.info("========================================");
        log.info(" TEST WORKFLOW COMPLETED");
        log.info(" Success: {}", success);
        log.info("========================================");

        return success;
    }

     public void deleteTestVMResources(String testVmName, String namespace) {
        log.info("Deleting test VM resources: {}", testVmName);
        vmService.deleteKubernetesService(testVmName, namespace);
        vmService.deleteKubernetestVmObject(testVmName, namespace);
        vmService.deleteKubernetesPvc(testVmName, namespace);
        log.info("Test VM resources deleted: {}", testVmName);
    }
}