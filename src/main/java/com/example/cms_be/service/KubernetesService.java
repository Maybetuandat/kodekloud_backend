package com.example.cms_be.service;

import org.springframework.stereotype.Service;

import io.kubernetes.client.Exec;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import com.example.cms_be.model.Lab;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class KubernetesService {

    private CoreV1Api api;   // api client for k8s cluster 
    private ApiClient client;  // doi tuong chua cac thong tin ket noi toi Kubernetes cluster
    private Exec exec; // Exec object để thực thi commands trong pod
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    @Value("${kubernetes.namespace:default}")
    private String namespace;

    @Value("${kubernetes.config.file.path:}")
    private String kubeConfigPath;

   
    @PostConstruct
    public void init() {
        try {
            if (StringUtils.hasText(kubeConfigPath)) {
                log.info("Using Kubernetes config from: {}", kubeConfigPath);
                client = loadConfigFromFile(kubeConfigPath);
            } else {
                log.info("Using default Kubernetes config");
                client = Config.defaultClient();
            }
            
            Configuration.setDefaultApiClient(client);
            api = new CoreV1Api();
            exec = new Exec(); // Khởi tạo Exec object
            log.info("Kubernetes client initialized successfully");
        } catch (Exception e) {
            log.error("Error initializing Kubernetes client: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize Kubernetes client", e);
        }
    }

    private ApiClient loadConfigFromFile(String configPath) throws Exception {
        File configFile = new File(configPath);
        
        if (!configFile.exists()) {
            throw new RuntimeException("Kubernetes config file not found at: " + configPath);
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            KubeConfig kubeConfig = KubeConfig.loadKubeConfig(reader);
            return ClientBuilder.kubeconfig(kubeConfig).build();
        } catch (Exception e) {
            log.error("Failed to load config from file {}: {}", configPath, e.getMessage());
            throw new RuntimeException("Failed to load Kubernetes config from file: " + configPath, e);
        }
    }

    // ============ POD MANAGEMENT OPERATIONS ============

    public String createLabPod(Lab lab) throws Exception {
        try {
            log.info("Create labpod for lab id {}", lab.getId());
            V1Pod pod = buildLabPod(lab);
            
            log.info("About to call createNamespacedPod API for namespace: {}", namespace);
            
            V1Pod createdPod = api.createNamespacedPod(namespace, pod, null, null, null, null);
            String podName = createdPod.getMetadata().getName();
            log.info("Successfully created pod: {} for lab: {}", podName, lab.getId());
            
            return podName;
        } catch (ApiException e) {
            log.error("=== Kubernetes API Exception Details ===");
            log.error("HTTP Status Code: {}", e.getCode());
            log.error("Response Body: {}", e.getResponseBody());
            log.error("Response Headers: {}", e.getResponseHeaders());
            log.error("Message: {}", e.getMessage());
            
            throw new Exception("Kubernetes API Error [" + e.getCode() + "]: " , e);
        } catch (Exception e) {
            log.error("General exception creating pod: ", e);
            throw new Exception("Failed to create lab pod: " + e.getMessage(), e);
        }
    }

    private V1Pod buildLabPod(Lab lab) {
        String nameLab = "lab-" + lab.getId() + "-" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        log.info("Creating pod with name: {}", nameLab);
        
        V1ObjectMeta metadata = new V1ObjectMeta()
                .name(nameLab)
                .namespace(namespace)
                .labels(createLabels(lab));

        V1Container container = new V1Container()
                .name(nameLab)
                .image(lab.getBaseImage())
                .command(java.util.Arrays.asList("/bin/bash", "-c", "sleep 3600"))
                .resources(createResourceRequirements());

        V1PodSpec podSpec = new V1PodSpec()
                .containers(java.util.Arrays.asList(container))
                .restartPolicy("Never");

        V1Pod pod = new V1Pod()
                .apiVersion("v1")
                .kind("Pod")
                .metadata(metadata)
                .spec(podSpec);
        
        log.info("Pod created with metadata: {}, spec: {}", metadata, podSpec);
        return pod;
    }

    private V1ResourceRequirements createResourceRequirements() {
        Map<String, io.kubernetes.client.custom.Quantity> requests = new HashMap<>();
        Map<String, io.kubernetes.client.custom.Quantity> limits = new HashMap<>();
        
         requests.put("cpu", new io.kubernetes.client.custom.Quantity("500m"));      // Tăng từ 100m lên 500m
    requests.put("memory", new io.kubernetes.client.custom.Quantity("1Gi"));    // Tăng từ 128Mi lên 1Gi
    
    limits.put("cpu", new io.kubernetes.client.custom.Quantity("2"));           // Tăng từ 500m lên 2 cores
    limits.put("memory", new io.kubernetes.client.custom.Quantity("4Gi")); 

        return new V1ResourceRequirements()
                .requests(requests)
                .limits(limits);
    }

    private Map<String, String> createLabels(Lab lab) {
        Map<String, String> labels = new HashMap<>();
        labels.put("lab-id", lab.getId());
        labels.put("created-by", "admin");
        return labels;
    }

    public String getPodStatus(String podName) throws Exception {
        try {
            V1Pod pod = api.readNamespacedPod(podName, namespace, null);
            return pod.getStatus().getPhase();
        } catch (Exception e) {
            log.error("Failed to get pod status for {}: {}", podName, e.getMessage());
            throw new Exception("Failed to get pod status: " + e.getMessage());
        }
    }

    public void deleteLabPod(String podName) throws Exception {
        try {
            log.info("Deleting pod: {}", podName);
            api.deleteNamespacedPod(podName, namespace, null, null, null, null, null, null);
            log.info("Successfully deleted pod: {}", podName);
        } catch (Exception e) {
            log.error("Failed to delete pod {}: {}", podName, e.getMessage());
            throw new Exception("Failed to delete pod: " + e.getMessage());
        }
    }

    // ============ POD READINESS OPERATIONS ============

    /**
     * Đợi pod sẵn sàng để thực thi commands với improved logging
     */
    public void waitForPodReady(String podName) throws Exception {
        log.info("Waiting for pod {} to be ready...", podName);
        
        for (int i = 0; i < 60; i++) { // Max 60 attempts (60 seconds)
            try {
                var pod = api.readNamespacedPod(podName, namespace, null);
                String phase = pod.getStatus().getPhase();
                
                log.debug("Pod {} status check {}/60: phase = {}", podName, i + 1, phase);
                
                if ("Running".equals(phase)) {
                    log.info("Pod {} is ready and running", podName);
                    // Additional check for container readiness
                    Thread.sleep(5000); // Wait 5 more seconds for containers to be fully ready
                    return;
                }
                
                if ("Failed".equals(phase) || "Succeeded".equals(phase)) {
                    String errorMsg = "Pod is in " + phase + " state";
                    log.error("Pod {} failed to start: {}", podName, errorMsg);
                    throw new Exception(errorMsg);
                }
                
                Thread.sleep(1000); // Wait 1 second
                
            } catch (Exception e) {
                if (i >= 59) { // Last attempt
                    log.error("Pod {} did not become ready in time: {}", podName, e.getMessage());
                    throw new Exception("Pod did not become ready in time: " + e.getMessage());
                }
                log.debug("Pod readiness check failed (attempt {}): {}", i + 1, e.getMessage());
            }
        }
        
        throw new Exception("Pod did not become ready within 60 seconds");
    }

    // ============ COMMAND EXECUTION OPERATIONS ============

    /**
     * Thực thi command với live streaming của output
     */
    public Integer executeCommandInPod(String podName, String command, int timeoutSeconds, 
                                     CommandOutputHandler outputHandler) throws Exception {
        log.debug("Executing command in pod {}: {}", podName, command);

        try {
            String[] commandArray = {"/bin/bash", "-c", command};
            
            Process process = exec.exec(namespace, podName, commandArray, false, true);
            
            // Stream stdout in realtime
            CompletableFuture<Void> stdoutFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (outputHandler != null) {
                            outputHandler.onStdout(line);
                        }
                        log.debug("STDOUT: {}", line);
                    }
                } catch (IOException e) {
                    log.error("Error reading stdout from pod {}: {}", podName, e.getMessage());
                }
            }, executorService);

            // Stream stderr in realtime  
            CompletableFuture<Void> stderrFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (outputHandler != null) {
                            outputHandler.onStderr(line);
                        }
                        log.warn("STDERR: {}", line);
                    }
                } catch (IOException e) {
                    log.error("Error reading stderr from pod {}: {}", podName, e.getMessage());
                }
            }, executorService);

            // Wait for process completion with timeout
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            
            if (!completed) {
                process.destroyForcibly();
                log.error("Command timed out after {} seconds in pod {}: {}", timeoutSeconds, podName, command);
                throw new Exception("Command execution timed out after " + timeoutSeconds + " seconds");
            }

            // Wait for stream readers to complete
            try {
                CompletableFuture.allOf(stdoutFuture, stderrFuture).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Stream readers did not complete within 5 seconds: {}", e.getMessage());
            }
            
            int exitCode = process.exitValue();
            log.debug("Command completed with exit code: {} in pod: {}", exitCode, podName);
            return exitCode;
            
        } catch (Exception e) {
            log.error("Error executing command in pod {}: {}", podName, e.getMessage());
            throw e;
        }
    }

    // ============ UTILITY METHODS ============

    /**
     * Interface để handle command output
     */
    public interface CommandOutputHandler {
        void onStdout(String line);
        void onStderr(String line);
    }

    /**
     * Get namespace being used
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Get API client
     */
    public CoreV1Api getApi() {
        return api;
    }

    /**
     * Cleanup resources
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}