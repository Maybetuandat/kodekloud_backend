package com.example.cms_be.service;

import com.example.cms_be.model.Lab;
import com.example.cms_be.ultil.PodLogWebSocketHandler;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Yaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class VMTestResourceService {

    private final CustomObjectsApi customApi;
    private final CoreV1Api coreApi;
    private final PodLogWebSocketHandler webSocketHandler;

    @Value("${KUBEVIRT_GROUP}")
    private String KUBEVIRT_GROUP;

    @Value("${KUBEVIRT_PLURAL_VM}")
    private String KUBEVIRT_PLURAL_VM;

    @Value("${KUBEVIRT_VERSION}")
    private String KUBEVIRT_VERSION;

    /**
     * Tạo toàn bộ resources cho test VM
     */
    public void createTestVMResources(Lab lab, String testVmName, String namespace) throws IOException, ApiException {
        log.info("Creating test VM resources: {}", testVmName);

        // Ensure namespace exists
        ensureNamespaceExists(namespace);
        webSocketHandler.broadcastLogToPod(testVmName, "info",
                "✓ Namespace verified: " + namespace, null);

        // Create PVC
        webSocketHandler.broadcastLogToPod(testVmName, "info",
                "⏳ Creating PersistentVolumeClaim...", null);
        createPvcForTest(testVmName, namespace, lab.getInstanceType().getStorageGb().toString());
        webSocketHandler.broadcastLogToPod(testVmName, "success",
                "✅ PVC created successfully", null);

        // Create VM
        webSocketHandler.broadcastLogToPod(testVmName, "info",
                "⏳ Creating VirtualMachine...", null);
        createVirtualMachineForTest(testVmName, namespace,
                lab.getInstanceType().getMemoryGb().toString(),
                lab.getInstanceType().getCpuCores().toString());
        webSocketHandler.broadcastLogToPod(testVmName, "success",
                "✅ VirtualMachine created successfully", null);

        // Create SSH Service
        webSocketHandler.broadcastLogToPod(testVmName, "info",
                "⏳ Creating SSH service...", null);
        createSshServiceForTest(testVmName, namespace);
        webSocketHandler.broadcastLogToPod(testVmName, "success",
                "✅ SSH service created successfully", null);

        log.info("Test VM resources created successfully: {}", testVmName);
    }

    /**
     * Xóa toàn bộ resources của test VM
     */
    public void deleteTestVMResources(String testVmName, String namespace) {
        log.info("Deleting test VM resources: {}", testVmName);

        // Delete Service
        try {
            String serviceName = "ssh-" + testVmName;
            log.info("Deleting Service: {}", serviceName);
            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    "⏳ Deleting SSH service...", null);
            coreApi.deleteNamespacedService(serviceName, namespace, null, null, null, null, null, null);
            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    "✓ SSH service deleted", null);
        } catch (ApiException e) {
            log.warn("Failed to delete Service: {} (may already be deleted)", e.getResponseBody());
        }

        // Delete VirtualMachine
        try {
            log.info("Deleting VirtualMachine: {}", testVmName);
            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    "⏳ Deleting VirtualMachine...", null);
            customApi.deleteNamespacedCustomObject(
                    KUBEVIRT_GROUP,
                    KUBEVIRT_VERSION,
                    namespace,
                    KUBEVIRT_PLURAL_VM,
                    testVmName,
                    null, null, null, null, null
            );
            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    "✓ VirtualMachine deleted", null);
        } catch (ApiException e) {
            log.warn("Failed to delete VirtualMachine: {} (may already be deleted)", e.getResponseBody());
        }

        // Delete PVC
        try {
            log.info("Deleting PVC: {}", testVmName);
            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    "⏳ Deleting PersistentVolumeClaim...", null);
            coreApi.deleteNamespacedPersistentVolumeClaim(testVmName, namespace, null, null, null, null, null, null);
            webSocketHandler.broadcastLogToPod(testVmName, "info",
                    "✓ PVC deleted", null);
        } catch (ApiException e) {
            log.warn("Failed to delete PVC: {} (may already be deleted)", e.getResponseBody());
        }

        log.info("Test VM resources deleted: {}", testVmName);
    }

    // ===== PRIVATE HELPER METHODS =====

    private void createPvcForTest(String vmName, String namespace, String storage) throws IOException, ApiException {
        Map<String, String> values = Map.of(
                "NAME", vmName,
                "NAMESPACE", namespace,
                "STORAGE", storage
        );
        String pvcYaml = loadAndRenderTemplate("templates/pvc.yaml", values);
        V1PersistentVolumeClaim pvcBody = Yaml.loadAs(pvcYaml, V1PersistentVolumeClaim.class);

        log.info("Creating PersistentVolumeClaim '{}'", vmName);
        coreApi.createNamespacedPersistentVolumeClaim(namespace, pvcBody, null, null, null, null);
        log.info("PersistentVolumeClaim '{}' created", vmName);
    }

    private void createVirtualMachineForTest(String vmName, String namespace, String memory, String cpu)
            throws IOException, ApiException {
        Map<String, String> values = Map.of(
                "NAME", vmName,
                "NAMESPACE", namespace,
                "MEMORY", memory,
                "CPU", cpu
        );
        String vmYaml = loadAndRenderTemplate("templates/vm-template.yaml", values);
        @SuppressWarnings("rawtypes")
        Map vmBody = Yaml.loadAs(vmYaml, Map.class);

        log.info("Creating VirtualMachine '{}'", vmName);
        customApi.createNamespacedCustomObject(KUBEVIRT_GROUP, KUBEVIRT_VERSION, namespace, KUBEVIRT_PLURAL_VM, vmBody, null, null, null);
        log.info("VirtualMachine '{}' created", vmName);
    }

    private void createSshServiceForTest(String vmName, String namespace) throws ApiException {
        String serviceName = "ssh-" + vmName;

        V1Service service = new V1Service()
                .apiVersion("v1")
                .kind("Service")
                .metadata(new V1ObjectMeta()
                        .name(serviceName)
                        .namespace(namespace))
                .spec(new V1ServiceSpec()
                        .type("NodePort")
                        .addPortsItem(new V1ServicePort()
                                .protocol("TCP")
                                .port(22)
                                .targetPort(new io.kubernetes.client.custom.IntOrString(22)))
                        .selector(Map.of("app", vmName)));

        log.info("Creating SSH Service '{}'", serviceName);
        coreApi.createNamespacedService(namespace, service, null, null, null, null);
        log.info("SSH Service '{}' created", serviceName);
    }

    private void ensureNamespaceExists(String namespace) throws ApiException {
        try {
            coreApi.readNamespace(namespace, null);
            log.info("Namespace '{}' exists", namespace);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                log.info("Creating namespace '{}'", namespace);
                V1Namespace namespaceBody = new V1Namespace()
                        .apiVersion("v1")
                        .kind("Namespace")
                        .metadata(new V1ObjectMeta().name(namespace));
                coreApi.createNamespace(namespaceBody, null, null, null, null);
                log.info("Namespace '{}' created", namespace);
            } else {
                throw e;
            }
        }
    }

    private String loadAndRenderTemplate(String templatePath, Map<String, String> values) throws IOException {
        ClassPathResource resource = new ClassPathResource(templatePath);
        InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
        String template = FileCopyUtils.copyToString(reader);

        for (Map.Entry<String, String> entry : values.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }
}