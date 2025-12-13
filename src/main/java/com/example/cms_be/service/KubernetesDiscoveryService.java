package com.example.cms_be.service;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    
}