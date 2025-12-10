package com.example.cms_be.service;

import com.example.cms_be.model.Lab;
import com.example.cms_be.model.UserLabSession;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Yaml;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class VMService {

    private final CustomObjectsApi customApi;
    private final CoreV1Api coreApi;
    // Đã xóa NetworkingV1Api và BackendIpService vì không còn dùng NetworkPolicy

    @Getter
    @Value("${KUBEVIRT_GROUP}")
    private String KUBEVIRT_GROUP;

    @Getter
    @Value("${KUBEVIRT_PLURAL_VM}")
    private String KUBEVIRT_PLURAL_VM;

    @Getter
    @Value("${KUBEVIRT_VERSION}")
    private String KUBEVIRT_VERSION;

    @Getter
    @Value("${CDI_PLURAL_DV}")
    private String CDI_PLURAL_DV;

    @Getter
    @Value("${CDI_GROUP}")
    private String CDI_GROUP;

    @Getter
    @Value("${CDI_VERSION}")
    private String CDI_VERSION;

    @Value("${app.execution-environment}")
    private String executionEnvironment;

    public void createKubernetesResourcesForSession(UserLabSession session) throws IOException, ApiException {
        String vmName = "vm-" + session.getId();
        String namespace = session.getLab().getNamespace();
        Lab lab = session.getLab();

        if (lab.getInstanceType() != null) {
            lab.getInstanceType().getId();
            lab.getInstanceType().getStorageGb();
            lab.getInstanceType().getMemoryGb();
            lab.getInstanceType().getCpuCores();
        }

        // 1. Đảm bảo Namespace tồn tại
        ensureNamespaceExists(namespace);

        // 2. Tạo PVC (Ổ cứng)
        createPvcForSession(
                vmName,
                namespace,
                session.getLab().getInstanceType().getBackingImage().toString(),
                session.getLab().getInstanceType().getStorageGb().toString()
        );

        // 3. Tạo VM (Máy ảo)
        createVirtualMachineFromTemplate(vmName, namespace,
                lab.getInstanceType().getMemoryGb().toString(),
                lab.getInstanceType().getCpuCores().toString());

        log.info("Kubernetes resources created successfully for session {}", session.getId());
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

    public void createPvcForSession(String vmName, String namespace, String backingImage, String storage) throws IOException, ApiException {
        Map<String, String> values = Map.of(
                "NAME", vmName,
                "NAMESPACE", namespace,
                "BACKING_IMAGE", backingImage,
                "STORAGE", storage
        );
        String pvcYaml = loadAndRenderTemplate("templates/pvc.yaml", values);
        V1PersistentVolumeClaim pvcBody = Yaml.loadAs(pvcYaml, V1PersistentVolumeClaim.class);

        log.info("Creating PersistentVolumeClaim '{}' using StorageClass 'longhorn-ext4-backing'...", vmName);

        try {
            coreApi.createNamespacedPersistentVolumeClaim(namespace, pvcBody, null, null, null, null);
            log.info("PersistentVolumeClaim '{}' created successfully.", vmName);
        } catch (ApiException e) {
            // Nếu PVC đã tồn tại thì bỏ qua lỗi (Idempotency)
            if (e.getCode() == 409) {
                log.info("PersistentVolumeClaim '{}' already exists.", vmName);
            } else {
                log.error("K8S API Exception when creating PVC. Status code: {}. Response body: {}", e.getCode(), e.getResponseBody());
                throw e;
            }
        }
    }

    public void createVirtualMachineFromTemplate(String vmName, String namespace, String memory, String cpu) throws IOException, ApiException {
        Map<String, String> values = Map.of(
                "NAME", vmName,
                "NAMESPACE", namespace,
                "MEMORY", memory,
                "CPU", cpu
        );
        String virtualMachineYaml = loadAndRenderTemplate("templates/vm-template.yaml", values);
        @SuppressWarnings("rawtypes")
        Map vmBody = Yaml.loadAs(virtualMachineYaml, Map.class);

        log.info("Creating VirtualMachine '{}'...", vmName);

        try {
            customApi.createNamespacedCustomObject(KUBEVIRT_GROUP, KUBEVIRT_VERSION, namespace, KUBEVIRT_PLURAL_VM, vmBody, null, null, null);
            log.info("VirtualMachine '{}' definition created successfully.", vmName);
        } catch (ApiException e) {
            log.error("K8s error code: {}", e.getCode());
            log.error("K8s response body: {}", e.getResponseBody());
            log.error("Response headers: {}", e.getResponseHeaders());
            throw e;
        }
    }

    public void deleteKubernetesResourcesForSession(UserLabSession session) {
        String vmName = "vm-" + session.getId();
        String namespace = session.getLab().getNamespace();

        // Chỉ xóa VM và PVC, không còn Service để xóa
        deleteKubernetestVmObject(vmName, namespace);
        deleteKubernetesPvc(vmName, namespace);
        log.info("Deleted Kubernetes resources for session ID: {}", session.getId());
    }

    public void deleteKubernetesPvc(String pvcName, String namespace) {
        try {
            log.info("Deleting PersistentVolumeClaim: {} in namespace {}", pvcName, namespace);
            coreApi.deleteNamespacedPersistentVolumeClaim(pvcName, namespace, null, null, null, null, null, null);
        } catch (ApiException e) {
            log.warn("Failed to delete PersistentVolumeClaim {}: {} (May already be deleted)", pvcName, e.getResponseBody());
        }
    }

    public void deleteKubernetestVmObject(String vmName, String namespace) {
        try {
            log.info("Deleting VirtualMachine: {} in namespace {}", vmName, namespace);
            customApi.deleteNamespacedCustomObject(
                    KUBEVIRT_GROUP,
                    KUBEVIRT_VERSION,
                    namespace,
                    KUBEVIRT_PLURAL_VM,
                    vmName,
                    null, null, null, null, null
            );
        } catch (ApiException e) {
            log.warn("Failed to delete VirtualMachine {}: {} (May already be deleted)", vmName, e.getResponseBody());
        }
    }

    // this function ensures that the namespace exists; if not, it creates it
    public void ensureNamespaceExists(String namespace) throws ApiException {
        try {
            coreApi.readNamespace(namespace, null);
            log.info("Namespace '{}' already exists.", namespace);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                log.info("Namespace '{}' not found. Creating...", namespace);
                V1Namespace namespaceBody = new V1Namespace()
                        .apiVersion("v1")
                        .kind("Namespace")
                        .metadata(new V1ObjectMeta().name(namespace));
                coreApi.createNamespace(namespaceBody, null, null, null, null);
                log.info("Namespace '{}' created successfully.", namespace);
            } else {
                log.error("Error checking namespace '{}'. Status code: {}. Response body: {}",
                        namespace, e.getCode(), e.getResponseBody());
                throw e;
            }
        }
    }
}