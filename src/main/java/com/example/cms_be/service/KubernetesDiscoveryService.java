package com.example.cms_be.service;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

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
        V1Service service = coreApi.readNamespacedService("ssh-" + vmName, namespace, null);
        Integer nodePort = service.getSpec().getPorts().get(0).getNodePort();

        V1NodeList nodeList = coreApi.listNode(null, null, null, null, null, 1, null, null, null, null);
        String nodeIp = findNodeIp(nodeList.getItems().get(0));

        if (nodePort == null || nodeIp == null) {
            throw new IllegalStateException("Could not determine NodeIP and NodePort for external connection.");
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

    public record SshConnectionDetails(String host, int port) {}
}
