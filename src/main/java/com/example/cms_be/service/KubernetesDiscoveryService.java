package com.example.cms_be.service;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.example.cms_be.dto.connection.SshConnectionDetails;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

@Service
@Slf4j
@RequiredArgsConstructor
public class KubernetesDiscoveryService {

    private final CoreV1Api coreApi;

    public V1Pod waitForPodRunning(String vmName, String namespace, int timeoutSeconds) throws ApiException, InterruptedException {
        long startTime = System.currentTimeMillis();
        String labelSelector = "app=" + vmName;

        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000L) {
            V1PodList podList = coreApi.listNamespacedPod(namespace, null, null, null, null, labelSelector, 1, null, null, null, null);
            if (podList.getItems().isEmpty()) {
                log.info("Waiting for pod with label '{}' to be created...", labelSelector);
                Thread.sleep(5000);
                continue;
            }

            V1Pod pod = podList.getItems().get(0);
            String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";

            if ("Running".equals(phase)) {
                log.info("Pod '{}' is now Running.", pod.getMetadata().getName());
                return pod;
            }

            log.info("Pod '{}' is in phase '{}'. Waiting...", pod.getMetadata().getName(), phase);
            Thread.sleep(5000);
        }

        throw new RuntimeException("Timeout: Pod with label '" + labelSelector + "' did not enter Running state within " + timeoutSeconds + " seconds.");
    }

    // Thực hiện tại kết nối TCP để kiểm tra xem ssh đã sẵn sàng hay chưa
    public void waitForSshReady(String host, int port, int timeoutSeconds) throws InterruptedException {
        log.info("Waiting for SSH service to be ready at {}:{}...", host, port);
        long startTime = System.currentTimeMillis();
        boolean isSshReady = false;

        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000L) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 2000); // Timeout kết nối 2 giây
                isSshReady = true;
                log.info("SSH service is ready at {}:{}!", host, port);
                break;
            } catch (IOException e) {
                log.debug("SSH connection failed at {}:{}. Reason: {}", host, port, e.getMessage());
                Thread.sleep(5000);
            }
        }

        if (!isSshReady) {
            throw new RuntimeException("Timeout: SSH service did not become ready within " + timeoutSeconds + " seconds.");
        }
    }

    public SshConnectionDetails getExternalSshDetails(String vmName, String namespace) throws ApiException {
        log.info("Fetching external SSH details for VM '{}' in namespace '{}'", vmName, namespace);
        
        // Bước 1: Tìm pod VM và worker node chứa nó
        String workerNodeName = findWorkerNodeForVM(vmName, namespace);
        log.info("VM '{}' is running on worker node: {}", vmName, workerNodeName);
        
        // Bước 2: Lấy thông tin NodePort từ SSH service
        Integer nodePort = getNodePortFromService(vmName, namespace);
        log.info("SSH NodePort for VM '{}': {}", vmName, nodePort);
        
        // Bước 3: Lấy IP của worker node cụ thể
        String workerNodeIp = getWorkerNodeIp(workerNodeName);
        if (workerNodeIp == null) {
            throw new RuntimeException("Could not determine IP address for worker node: " + workerNodeName);
        }

        log.info("Found SSH connection details: {}:{} (worker: {})", workerNodeIp, nodePort, workerNodeName);
        return new SshConnectionDetails(workerNodeIp, nodePort);
    }
    
    /**
     * Tìm worker node đang chạy VM pod
     */
    private String findWorkerNodeForVM(String vmName, String namespace) throws ApiException {
        String labelSelector = "app=" + vmName;
        
        try {
            V1PodList podList = coreApi.listNamespacedPod(namespace, null, null, null, null, labelSelector, null, null, null, null, null);
            
            if (podList.getItems().isEmpty()) {
                throw new RuntimeException("No pods found for VM: " + vmName + " with label selector: " + labelSelector);
            }
            
            // Lấy pod đầu tiên (virt-launcher pod)
            V1Pod vmPod = podList.getItems().get(0);
            String nodeName = vmPod.getSpec().getNodeName();
            
            if (nodeName == null || nodeName.isEmpty()) {
                throw new RuntimeException("Pod for VM '" + vmName + "' is not scheduled to any node yet");
            }
            
            log.info("Found VM pod '{}' running on node: {}", vmPod.getMetadata().getName(), nodeName);
            return nodeName;
            
        } catch (ApiException e) {
            log.error("Failed to find pod for VM '{}': HTTP {}", vmName, e.getCode());
            throw new RuntimeException("Failed to locate VM pod: " + e.getMessage());
        }
    }
    
    /**
     * Lấy NodePort từ SSH service
     */
    private Integer getNodePortFromService(String vmName, String namespace) throws ApiException {
        String serviceName = "ssh-" + vmName;
        
        try {
            V1Service service = coreApi.readNamespacedService(serviceName, namespace, null);
            
            // Kiểm tra service có port không
            if (service.getSpec() == null || service.getSpec().getPorts() == null || service.getSpec().getPorts().isEmpty()) {
                throw new RuntimeException("SSH service exists but has no ports configured: " + serviceName);
            }
            
            Integer nodePort = service.getSpec().getPorts().get(0).getNodePort();
            if (nodePort == null) {
                throw new RuntimeException("SSH service exists but NodePort is not assigned: " + serviceName);
            }
            
            return nodePort;
            
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                log.error("SSH Service '{}' not found in namespace '{}'. The VM may not be ready yet.", serviceName, namespace);
                throw new RuntimeException("SSH service not found. VM is still being created or failed to start. Service: " + serviceName);
            } else {
                log.error("Failed to read SSH service '{}': HTTP {}", serviceName, e.getCode());
                throw new RuntimeException("Failed to access SSH service due to Kubernetes API error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Lấy IP của worker node cụ thể theo tên
     */
    private String getWorkerNodeIp(String nodeName) throws ApiException {
        try {
            V1Node node = coreApi.readNode(nodeName, null);
            return findNodeIp(node);
            
        } catch (ApiException e) {
            log.error("Failed to get node '{}': HTTP {}", nodeName, e.getCode());
            throw new RuntimeException("Failed to get worker node information: " + e.getMessage());
        }
    }

    public SshConnectionDetails getInternalSshDetails(V1Pod pod) {
        log.info("Fetching internal SSH details for Pod '{}'", pod.getMetadata().getName());
        String podIp = pod.getStatus().getPodIP();
        if (podIp == null) {
            throw new IllegalStateException("Pod IP is not available for internal connection.");
        }
        log.info("Found internal connection details: {}:22", podIp);
        return new SshConnectionDetails(podIp, 22);
    }

    public String findNodeIp(V1Node node) {
        if (node.getStatus() == null || node.getStatus().getAddresses() == null) {
            return null;
        }
        
        String externalIp = null;
        String internalIp = null;

        for (V1NodeAddress address : node.getStatus().getAddresses()) {
            if ("ExternalIP".equals(address.getType())) {
                externalIp = address.getAddress();
            }
            if ("InternalIP".equals(address.getType())) {
                internalIp = address.getAddress();
            }
        }

        return externalIp != null ? externalIp : internalIp;
    }
}