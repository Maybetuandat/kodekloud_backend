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
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class VMService {
    
    private final CustomObjectsApi customApi;
    private final CoreV1Api coreApi;
    private final NetworkingV1Api networkingApi;
    private final BackendIpService backendIpService;
    
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
        ensureNamespaceExists(namespace);
        createNetworkPolicyForNamespace(namespace);
        createPvcForSession(vmName, namespace, session.getLab().getInstanceType().getStorageGb().toString());
        createVirtualMachineFromTemplate(vmName, namespace, lab.getInstanceType().getMemoryGb().toString(), lab.getInstanceType().getCpuCores().toString());
        createSshServiceForVM(vmName, namespace);
    }

 
    private void createNetworkPolicyForNamespace(String namespace) throws IOException, ApiException {
        try {

            // get existing NetworkPolicy
            V1NetworkPolicy existingPolicy = networkingApi.readNamespacedNetworkPolicy("lab-vm-secure-policy", namespace, null);
            log.info("NetworkPolicy 'lab-vm-secure-policy' already exists in namespace '{}'", namespace);
            
            if (needsBackendIpUpdate(existingPolicy)) {
                updateNetworkPolicyWithBackendIps(existingPolicy, namespace);
            }
            return;
            
        } catch (ApiException e) {
            if (e.getCode() != 404) {
                throw e;
            }
        }

        log.info("Creating new NetworkPolicy with backend SSH ingress for namespace '{}'...", namespace);
        createNewNetworkPolicyWithBackendIps(namespace);
    }

    //  Tạo NetworkPolicy mới với backend SSH ingress
    private void createNewNetworkPolicyWithBackendIps(String namespace) throws ApiException {
        List<String> backendIps = backendIpService.getBackendIpAddresses();
        log.info("Creating NetworkPolicy with backend SSH ingress IPs: {}", backendIps);

        V1NetworkPolicy networkPolicy = new V1NetworkPolicy()
            .apiVersion("networking.k8s.io/v1")
            .kind("NetworkPolicy")
            .metadata(new V1ObjectMeta().name("lab-vm-secure-policy").namespace(namespace))
            .spec(new V1NetworkPolicySpec()
                .podSelector(new V1LabelSelector()) // Apply to all pods
                .addPolicyTypesItem("Ingress")
                .addPolicyTypesItem("Egress")
                
               
                .ingress(createBackendSshIngressRules(backendIps))
                
                //  EGRESS: DNS + Internet (mặc định)
                .egress(createDefaultEgressRules())
            );

        networkingApi.createNamespacedNetworkPolicy(namespace, networkPolicy, null, null, null, null);
        log.info("NetworkPolicy created successfully with backend SSH ingress for namespace '{}'", namespace);
    }

    //  INGRESS: Chỉ backend IPs được SSH vào VM
    private List<V1NetworkPolicyIngressRule> createBackendSshIngressRules(List<String> backendIps) {
        List<V1NetworkPolicyIngressRule> ingressRules = new ArrayList<>();
        
        if (backendIps.isEmpty()) {
            log.warn("No backend IPs detected. NetworkPolicy will block all SSH ingress.");
            return ingressRules;
        }
        
        // SSH ingress rule: chỉ từ backend IPs
        V1NetworkPolicyIngressRule sshIngressRule = new V1NetworkPolicyIngressRule()
            .addPortsItem(new V1NetworkPolicyPort().protocol("TCP").port(new IntOrString(22)));
        
        // Thêm mỗi backend IP
        for (String ip : backendIps) {
            sshIngressRule.addFromItem(new V1NetworkPolicyPeer()
                .ipBlock(new V1IPBlock().cidr(ip + "/24")));
        }
        sshIngressRule.addFromItem(new V1NetworkPolicyPeer()
                .ipBlock(new V1IPBlock().cidr("192.168.42.0/24")));
        ingressRules.add(sshIngressRule);
        
        log.info("Created SSH ingress rules for backend IPs: {}", backendIps);
        return ingressRules;
    }

    // EGRESS: DNS + Internet (theo yêu cầu mặc định)
    private List<V1NetworkPolicyEgressRule> createDefaultEgressRules() {
        List<V1NetworkPolicyEgressRule> egressRules = new ArrayList<>();

        // Rule 1: DNS (UDP + TCP port 53)
        egressRules.add(new V1NetworkPolicyEgressRule()
            .addPortsItem(new V1NetworkPolicyPort().protocol("UDP").port(new IntOrString(53)))
            .addPortsItem(new V1NetworkPolicyPort().protocol("TCP").port(new IntOrString(53))));

        // Rule 2: Internet access (exclude private ranges)
        egressRules.add(new V1NetworkPolicyEgressRule()
            .addToItem(new V1NetworkPolicyPeer()
                .ipBlock(new V1IPBlock()
                    .cidr("0.0.0.0/0")
                    .addExceptItem("10.0.0.0/8")
                    .addExceptItem("172.16.0.0/12") 
                    .addExceptItem("192.168.0.0/16")
                    .addExceptItem("169.254.0.0/16")
                    .addExceptItem("127.0.0.0/8"))));

        log.info("Created default egress rules: DNS + Internet (exclude private ranges)");
        return egressRules;
    }

  

    // check if existing network policy has current ip address in list: if not, return true to update
    private boolean needsBackendIpUpdate(V1NetworkPolicy existingPolicy) {
        List<String> currentBackendIps = backendIpService.getBackendIpAddresses();
        List<String> existingBackendIps = extractBackendIpsFromPolicy(existingPolicy);
        
        boolean needsUpdate = !new HashSet<>(currentBackendIps).equals(new HashSet<>(existingBackendIps));
        
        if (needsUpdate) {
            log.info("NetworkPolicy needs update. Current backend IPs: {}, Existing IPs: {}", 
                    currentBackendIps, existingBackendIps);
        } else {
            log.info("NetworkPolicy is up to date with backend IPs: {}", currentBackendIps);
        }
        
        return needsUpdate;
    }


    // this function extracts backend IPs from existing NetworkPolicy's SSH ingress rules
 
    private List<String> extractBackendIpsFromPolicy(V1NetworkPolicy policy) {
        List<String> backendIps = new ArrayList<>();
        
        if (policy.getSpec() == null || policy.getSpec().getIngress() == null) {
            return backendIps;
        }
        
        for (V1NetworkPolicyIngressRule ingressRule : policy.getSpec().getIngress()) {
            if (ingressRule.getPorts() != null && isSshIngressRule(ingressRule)) {
                if (ingressRule.getFrom() != null) {
                    for (V1NetworkPolicyPeer peer : ingressRule.getFrom()) {
                        if (peer.getIpBlock() != null && peer.getIpBlock().getCidr() != null) {
                            String cidr = peer.getIpBlock().getCidr();
                            if (cidr.endsWith("/32")) {
                                backendIps.add(cidr.replace("/32", ""));
                                log.debug("Extracted backend IP from NetworkPolicy: {}", cidr.replace("/32", ""));
                            }
                        }
                    }
                }
            }
        }
        
        return backendIps;
    }

    
    private boolean isSshIngressRule(V1NetworkPolicyIngressRule rule) {
        if (rule.getPorts() == null) return false;
        
        return rule.getPorts().stream().anyMatch(port -> 
            "TCP".equals(port.getProtocol()) && 
            port.getPort() != null && 
            port.getPort().getIntValue() == 22
        );
    }

    
    private void updateNetworkPolicyWithBackendIps(V1NetworkPolicy existingPolicy, String namespace) throws ApiException {
        List<String> backendIps = backendIpService.getBackendIpAddresses();
        
        // Update ingress rules
        existingPolicy.getSpec().setIngress(createBackendSshIngressRules(backendIps));
        
        // Keep existing egress rules hoặc set default
        if (existingPolicy.getSpec().getEgress() == null) {
            existingPolicy.getSpec().setEgress(createDefaultEgressRules());
        }
        
        networkingApi.replaceNamespacedNetworkPolicy("lab-vm-secure-policy", namespace, existingPolicy, null, null, null, null);
        log.info("NetworkPolicy updated with new backend SSH ingress IPs: {}", backendIps);
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

    public void createPvcForSession(String vmName, String namespace, String storage) throws IOException, ApiException {
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

   public void createSshServiceForVM(String name, String namespace) throws ApiException {
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
                            .targetPort(new IntOrString(defaultSshPort)))
                    .externalTrafficPolicy("Local"));

    log.info("Creating Service '{}'...", serviceName);
    coreApi.createNamespacedService(namespace, serviceBody, null, null, null, null);
    log.info("Service '{}' created successfully.", serviceName);
}

    public void deleteKubernetesResourcesForSession(UserLabSession session) {
        String vmName = "vm-" + session.getId();
        String namespace = session.getLab().getNamespace();
        
        deleteKubernetesService(vmName, namespace);
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

    public void deleteKubernetesService(String vmName, String namespace) {
        String serviceName = "ssh-" + vmName;
        try {
            log.info("Deleting Service: {} in namespace {}", serviceName, namespace);
            coreApi.deleteNamespacedService(serviceName, namespace, null, null, null, null, null, null);
        } catch (ApiException e) {
            log.warn("Failed to delete Service {}: {} (May already be deleted)", serviceName, e.getResponseBody());
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