package com.example.cms_be.service;
import com.example.cms_be.model.Lab;
import com.example.cms_be.model.UserLabSession;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
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

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class VMService {
    private final CustomObjectsApi customApi;
    private final CoreV1Api coreApi;
    private static final int defaultSshPort = 22;

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
    private final NetworkingV1Api networkingApi;





    @Value("${app.execution-environment}")
    private String executionEnvironment;

    public void createKubernetesResourcesForSession(UserLabSession session) throws IOException, ApiException {
        String vmName = "vm-" + session.getId();
        String namespace = session.getLab().getNamespace();
        Lab lab = session.getLab();
        ensureNamespaceExists(namespace);  //  đảm bảo namespace đã tồn tài
        createNetworkPolicyForNamespace(namespace); // tạo network policy
        createPvcForSession(vmName, namespace, session.getLab().getInstanceType().getStorageGb().toString());   // tạo pvc
        createVirtualMachineFromTemplate(vmName, namespace, lab.getInstanceType().getMemoryGb().toString(), lab.getInstanceType().getCpuCores().toString());  // tạo vm 
        createSshServiceForVM(vmName, namespace);  // tạo ssh service qua nodePort để map vào ssh trong vm 
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

    private void createPvcForSession(String vmName, String namespace, String storage) throws IOException, ApiException {
        Map<String, String> values = Map.of(
                "NAME", vmName,
                "NAMESPACE", namespace,
                "STORAGE", storage

        );
        String pvcYaml = loadAndRenderTemplate("templates/pvc.yaml", values);

        V1PersistentVolumeClaim pvcBody = Yaml.loadAs(pvcYaml, V1PersistentVolumeClaim.class);

        log.info("Creating PersistentVolumeClaim '{}' using StorageClass 'longhorn-ext4-backing'...", vmName);

        try {
            coreApi.createNamespacedPersistentVolumeClaim(namespace, pvcBody, null, null, null, null);
            log.info("PersistentVolumeClaim '{}' created successfully.", vmName);
        } catch (ApiException e) {
            log.error("K8S API Exception when creating PVC. Status code: {}. Response body: {}", e.getCode(), e.getResponseBody());
            throw e;
        }
    }




  
    private void createVirtualMachineFromTemplate(String vmName, String namespace, String memory, String cpu) throws IOException, ApiException {
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

    private void createSshServiceForVM(String name, String namespace) throws ApiException {
        String serviceName = "ssh-" + name;
        V1Service serviceBody = new V1Service()
                .apiVersion("v1")
                .kind("Service")
                .metadata(new V1ObjectMeta().name(serviceName).namespace(namespace))
                .spec(new V1ServiceSpec()
                        .type("NodePort")
                        .putSelectorItem("app", name)
                        .addPortsItem(new V1ServicePort()
                                .protocol("TCP")
                                .port(defaultSshPort)
                                .targetPort(new IntOrString(defaultSshPort))));

        log.info("Creating Service '{}'...", serviceName);
        coreApi.createNamespacedService(namespace, serviceBody, null, null, null, null);
        log.info("Service '{}' created successfully.", serviceName);
    }

    public void deleteKubernetesResourcesForSession(UserLabSession session) {
        String vmName = "vm-" + session.getId();
        String namespace = session.getLab().getNamespace();
        String serviceName = "ssh-" + vmName;

        // Xóa Service
        try {
            log.info("Deleting Service: {} in namespace {}", serviceName, namespace);
            coreApi.deleteNamespacedService(serviceName, namespace, null, null, null, null, null, null);
        } catch (ApiException e) {
            log.warn("Failed to delete Service {}: {} (May already be deleted)", serviceName, e.getResponseBody());
        }

        
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

        try {
            log.info("Deleting PersistentVolumeClaim: {} in namespace {}", vmName, namespace);
            coreApi.deleteNamespacedPersistentVolumeClaim(vmName, namespace, null, null, null, null, null, null);
        } catch (ApiException e) {
            log.warn("Failed to delete PVC {}: {} (May already be deleted)", vmName, e.getResponseBody());
        }
    }



    private void createNetworkPolicyForNamespace(String namespace ) throws IOException, ApiException
    {
          try {
            networkingApi.readNamespacedNetworkPolicy("lab-vm-secure-policy", namespace, null);
            log.info("NetworkPolicy 'lab-vm-secure-policy' already exists in namespace '{}'", namespace);
            return;
        } catch (ApiException e) {
            if (e.getCode() != 404) {
                throw e; 
            }
            
        }

        Map<String, String> values = Map.of("NAMESPACE", namespace);
        String networkPolicyYaml = loadAndRenderTemplate("templates/network-policy.yaml", values);
        
        V1NetworkPolicy networkPolicy = Yaml.loadAs(networkPolicyYaml, V1NetworkPolicy.class);

        log.info("Creating NetworkPolicy 'lab-vm-secure-policy' in namespace '{}'...", namespace);
        
        try {
            networkingApi.createNamespacedNetworkPolicy(namespace, networkPolicy, null, null, null, null);
            log.info("NetworkPolicy 'lab-vm-secure-policy' created successfully in namespace '{}'", namespace);
        } catch (ApiException e) {
            log.error("Failed to create NetworkPolicy in namespace '{}'. Status code: {}. Response body: {}", 
                    namespace, e.getCode(), e.getResponseBody());
            throw e;
        }
    }
    // Thực hiện kiểm tra xem namespace đã được tạo chưa, nếu chưa thực hiện tạo
    private void ensureNamespaceExists(String namespace) throws ApiException {
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