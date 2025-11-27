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
                log.debug("SSH not ready yet at {}:{}. Retrying in 5 seconds...", host, port);
                Thread.sleep(5000);
            }
        }

        if (!isSshReady) {
            throw new RuntimeException("Timeout: SSH service did not become ready within " + timeoutSeconds + " seconds.");
        }
    }

    public SshConnectionDetails getExternalSshDetails(String vmName, String namespace) throws ApiException {
        log.info("Fetching external SSH details for VM '{}'", vmName);
        
        String serviceName = "ssh-" + vmName;
        V1Service service;
        
        try {
            service = coreApi.readNamespacedService(serviceName, namespace, null);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                log.error("SSH Service '{}' not found in namespace '{}'. The VM may not be ready yet.", serviceName, namespace);
                throw new RuntimeException("SSH service not found. VM is still being created or failed to start. Service: " + serviceName);
            } else {
                log.error("Failed to read SSH service '{}': HTTP {}", serviceName, e.getCode());
                throw new RuntimeException("Failed to access SSH service due to Kubernetes API error: " + e.getMessage());
            }
        }
        
        // Kiểm tra service có port không
        if (service.getSpec() == null || service.getSpec().getPorts() == null || service.getSpec().getPorts().isEmpty()) {
            throw new RuntimeException("SSH service exists but has no ports configured: " + serviceName);
        }
        
        Integer nodePort = service.getSpec().getPorts().get(0).getNodePort();
        if (nodePort == null) {
            throw new RuntimeException("SSH service exists but NodePort is not assigned: " + serviceName);
        }

        V1NodeList nodeList;
        try {
            nodeList = coreApi.listNode(null, null, null, null, null, 1, null, null, null, null);
        } catch (ApiException e) {
            log.error("Failed to list Kubernetes nodes: HTTP {}", e.getCode());
            throw new RuntimeException("Failed to get Kubernetes nodes: " + e.getMessage());
        }
        
        if (nodeList.getItems().isEmpty()) {
            throw new RuntimeException("No Kubernetes nodes found in the cluster");
        }
        
        String nodeIp = findNodeIp(nodeList.getItems().get(0));
        if (nodeIp == null) {
            throw new RuntimeException("Could not determine IP address for Kubernetes node");
        }

        log.info("Found external connection details: {}:{}", nodeIp, nodePort);
        return new SshConnectionDetails(nodeIp, nodePort);
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