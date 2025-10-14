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
import io.kubernetes.client.openapi.models.V1SecurityContext;
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
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class KubernetesService {

    private CoreV1Api api;
    private ApiClient client;
    private Exec exec;
    
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
            exec = new Exec();
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

    public ApiClient getApiClient() {
        return this.client;
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

        // V1Container container = new V1Container()
        //         .name(nameLab)
        //         .image(lab.getBaseImage())
        //         .command(java.util.Arrays.asList("/bin/bash", "-c", "sleep 3600"))
        //         .resources(createResourceRequirements());

        V1Container container = new V1Container()
            .name(nameLab)
            .image("docker:dind") // Thay đổi image
            .command(java.util.Arrays.asList("dockerd-entrypoint.sh"))
            .securityContext(new V1SecurityContext()
                .privileged(true)) // BẮT BUỘC cho DinD
            .resources(createResourceRequirements());

        V1PodSpec podSpec = new V1PodSpec()
            .containers(java.util.Arrays.asList(container))
            .restartPolicy("Never")
            .runtimeClassName("kata-containers");

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
        
        requests.put("cpu", new io.kubernetes.client.custom.Quantity("500m"));
        requests.put("memory", new io.kubernetes.client.custom.Quantity("1Gi"));
        
        limits.put("cpu", new io.kubernetes.client.custom.Quantity("2"));
        limits.put("memory", new io.kubernetes.client.custom.Quantity("4Gi"));

        return new V1ResourceRequirements()
                .requests(requests)
                .limits(limits);
    }

    private Map<String, String> createLabels(Lab lab) {
        Map<String, String> labels = new HashMap<>();
        labels.put("lab-id", lab.getId().toString());
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

    public void waitForPodReady(String podName) throws Exception {
        log.info("Waiting for pod {} to be ready...", podName);
        
        for (int i = 0; i < 60; i++) {
            try {
                var pod = api.readNamespacedPod(podName, namespace, null);
                String phase = pod.getStatus().getPhase();
                
                log.debug("Pod {} status check {}/60: phase = {}", podName, i + 1, phase);
                
                if ("Running".equals(phase)) {
                    log.info("Pod {} is ready and running", podName);
                    Thread.sleep(5000);
                    return;
                }
                
                if ("Failed".equals(phase) || "Succeeded".equals(phase)) {
                    String errorMsg = "Pod is in " + phase + " state";
                    log.error("Pod {} failed to start: {}", podName, errorMsg);
                    throw new Exception(errorMsg);
                }
                
                Thread.sleep(1000);
                
            } catch (Exception e) {
                if (i >= 59) {
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
     * Command execution result với đầy đủ output
     */
    public static class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final String combinedOutput;
        
        public CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.combinedOutput = stdout + (stderr.isEmpty() ? "" : "\n" + stderr);
        }
        
        public int getExitCode() { return exitCode; }
        public String getStdout() { return stdout; }
        public String getStderr() { return stderr; }
        public String getCombinedOutput() { return combinedOutput; }
    }

    /**
     * Interface để handle command output
     */
    public interface CommandOutputHandler {
        void onStdout(String line);
        void onStderr(String line);
    }

    /**
     * Thực thi command và TRẢ VỀ ĐẦY ĐỦ OUTPUT với REAL-TIME LOGGING
     */
    // Fix trong method executeCommandInPodWithOutput:

public CommandResult executeCommandInPodWithOutput(String podName, String command, int timeoutSeconds, 
                                                 CommandOutputHandler outputHandler) throws Exception {
    
    log.info("==========================================");
    log.info("🚀 EXECUTING COMMAND IN POD: {}", podName);
    log.info("📝 COMMAND: {}", command);
    log.info("⏱️ TIMEOUT: {} seconds", timeoutSeconds);
    log.info("🔧 NAMESPACE: {}", namespace);
    log.info("==========================================");

    try {
        String[] commandArray = {"/bin/sh", "-c", command};
        
        log.debug("🔍 DEBUG: Exec object initialized: {}", exec != null);
        log.debug("🔍 DEBUG: API client initialized: {}", client != null);
        log.debug("🔍 DEBUG: Command array: {}", java.util.Arrays.toString(commandArray));
        
        // ===== FIX: THAY ĐỔI TTY CONFIGURATION =====
        // Thay vì: Process process = exec.exec(namespace, podName, commandArray, false, true);
        // Sử dụng: stdin=true, tty=false để capture output properly
        Process process = exec.exec(namespace, podName, commandArray, true, false);
        log.debug("🔍 DEBUG: Process created with stdin=true, tty=false");
        
        // Kiểm tra process streams
        log.debug("🔍 DEBUG: Process created successfully: {}", process != null);
        log.debug("🔍 DEBUG: InputStream available: {}", process.getInputStream() != null);
        log.debug("🔍 DEBUG: ErrorStream available: {}", process.getErrorStream() != null);
        
        // Thu thập output với improved handling
        StringBuilder stdoutOutput = new StringBuilder();
        StringBuilder stderrOutput = new StringBuilder();
        
        // ===== ENHANCED STDOUT READER =====
        Thread stdoutReader = new Thread(() -> {
            log.debug("🔍 DEBUG: STDOUT Reader thread started");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineNumber = 1;
                log.debug("🔍 DEBUG: Starting to read STDOUT lines...");
                
                while ((line = reader.readLine()) != null) {
                    synchronized (stdoutOutput) {
                        stdoutOutput.append(line).append("\n");
                    }
                    
                    // IMMEDIATE LOGGING cho mỗi line
                    log.info("📤 STDOUT[{}]: {}", lineNumber++, line);
                    
                    if (outputHandler != null) {
                        try {
                            outputHandler.onStdout(line);
                        } catch (Exception e) {
                            log.warn("Handler error: {}", e.getMessage());
                        }
                    }
                }
                log.debug("🔍 DEBUG: STDOUT Reader finished - total lines: {}", lineNumber - 1);
            } catch (IOException e) {
                log.error("❌ Error reading stdout: {}", e.getMessage(), e);
            }
        }, "stdout-reader-" + podName);
        
        // ===== ENHANCED STDERR READER =====
        Thread stderrReader = new Thread(() -> {
            log.debug("🔍 DEBUG: STDERR Reader thread started");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                int lineNumber = 1;
                log.debug("🔍 DEBUG: Starting to read STDERR lines...");
                
                while ((line = reader.readLine()) != null) {
                    synchronized (stderrOutput) {
                        stderrOutput.append(line).append("\n");
                    }
                    
                    // IMMEDIATE LOGGING cho mỗi line
                    log.warn("🔴 STDERR[{}]: {}", lineNumber++, line);
                    
                    if (outputHandler != null) {
                        try {
                            outputHandler.onStderr(line);
                        } catch (Exception e) {
                            log.warn("Handler error: {}", e.getMessage());
                        }
                    }
                }
                log.debug("🔍 DEBUG: STDERR Reader finished - total lines: {}", lineNumber - 1);
            } catch (IOException e) {
                log.error("❌ Error reading stderr: {}", e.getMessage(), e);
            }
        }, "stderr-reader-" + podName);
        
        // Start threads
        log.debug("🔍 DEBUG: Starting reader threads");
        stdoutReader.start();
        stderrReader.start();
        
        // ===== ENHANCED PROCESS WAITING =====
        log.debug("🔍 DEBUG: Waiting for process completion...");
        boolean processCompleted = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        log.debug("🔍 DEBUG: Process completed: {}", processCompleted);
        
        if (!processCompleted) {
            log.error("⏰ PROCESS TIMED OUT - killing process and threads");
            process.destroyForcibly();
            stdoutReader.interrupt();
            stderrReader.interrupt();
            throw new Exception("Command execution timed out after " + timeoutSeconds + " seconds");
        }
        
        // ===== ENHANCED THREAD JOINING =====
        log.debug("🔍 DEBUG: Waiting for reader threads to complete...");
        try {
            stdoutReader.join(5000); // Wait up to 5 seconds
            stderrReader.join(5000);
            log.debug("🔍 DEBUG: Reader threads joined successfully");
        } catch (InterruptedException e) {
            log.warn("⚠️ Reader threads interrupted during join");
            Thread.currentThread().interrupt();
        }
        
        // Force thread termination if still alive
        if (stdoutReader.isAlive()) {
            stdoutReader.interrupt();
            log.warn("⚠️ Forced to interrupt stdout reader thread");
        }
        if (stderrReader.isAlive()) {
            stderrReader.interrupt();
            log.warn("⚠️ Forced to interrupt stderr reader thread");
        }
        
        // ===== COLLECT RESULTS =====
        int exitCode = process.exitValue();
        String stdout, stderr;
        
        synchronized (stdoutOutput) {
            stdout = stdoutOutput.toString().trim();
        }
        synchronized (stderrOutput) {
            stderr = stderrOutput.toString().trim();
        }
        
        // ===== ENHANCED RESULT LOGGING =====
        log.info("==========================================");
        log.info("✅ COMMAND EXECUTION COMPLETED");
        log.info("📝 COMMAND: {}", command);
        log.info("🔢 EXIT CODE: {}", exitCode);
        log.info("📊 STDOUT LINES: {}", stdout.isEmpty() ? 0 : stdout.split("\n").length);
        log.info("📊 STDERR LINES: {}", stderr.isEmpty() ? 0 : stderr.split("\n").length);
        log.info("📊 TOTAL OUTPUT LENGTH: {} characters", stdout.length() + stderr.length());
        
        // Debug raw lengths
        log.debug("🔍 DEBUG: Raw STDOUT length: {} chars", stdout.length());
        log.debug("🔍 DEBUG: Raw STDERR length: {} chars", stderr.length());
        
        log.info("==========================================");
        
        // ===== LOG COMPLETE OUTPUT =====
        if (!stdout.isEmpty()) {
            log.info("📄 ===== COMPLETE STDOUT OUTPUT START =====");
            String[] stdoutLines = stdout.split("\n");
            for (int i = 0; i < stdoutLines.length; i++) {
                log.info("STDOUT[{}]: {}", i + 1, stdoutLines[i]);
            }
            log.info("📄 ===== COMPLETE STDOUT OUTPUT END =====");
        } else {
            log.warn("📄 ⚠️ No STDOUT output captured!");
            log.warn("📄 ⚠️ This might indicate:");
            log.warn("   - Command produces no output");
            log.warn("   - TTY/stdin configuration issue");
            log.warn("   - Kubernetes exec stream problem");
            log.warn("   - Process buffering issue");
        }
        
        if (!stderr.isEmpty()) {
            log.warn("📄 ===== COMPLETE STDERR OUTPUT START =====");
            String[] stderrLines = stderr.split("\n");
            for (int i = 0; i < stderrLines.length; i++) {
                log.warn("STDERR[{}]: {}", i + 1, stderrLines[i]);
            }
            log.warn("📄 ===== COMPLETE STDERR OUTPUT END =====");
        } else {
            log.info("📄 No STDERR output");
        }
        
        log.info("==========================================");
        
        return new CommandResult(exitCode, stdout, stderr);
        
    } catch (Exception e) {
        log.error("💥 ERROR EXECUTING COMMAND: {}", e.getMessage(), e);
        throw e;
    }
}
    /**
     * Backward compatibility method - chỉ trả về exit code
     */
    public Integer executeCommandInPod(String podName, String command, int timeoutSeconds, 
                                    CommandOutputHandler outputHandler) throws Exception {
        CommandResult result = executeCommandInPodWithOutput(podName, command, timeoutSeconds, outputHandler);
        return result.getExitCode();
    }

    /**
     * Simplified method để chỉ log output mà không cần handler
     */
    public CommandResult executeCommandWithLogs(String podName, String command, int timeoutSeconds) throws Exception {
        return executeCommandInPodWithOutput(podName, command, timeoutSeconds, null);
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
        log.info("KubernetesService shutdown completed");
    }
}