package com.example.cms_be.service;

import org.springframework.stereotype.Service;

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

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;





@Service
@Slf4j
public class KubernetesService {


    private CoreV1Api api;   // api client for k8s cluster 
    private ApiClient client;  // doi tuong chua cac thong tin ket noi toi Kubernetes cluster

    
    @Value("${kubernetes.namespace:default}")
    private String namespace;

    @Value("${kubernetes.config.file.path:}")
    private String kubeConfigPath;

   
    @PostConstruct
    public void init()
    {
        try 
        {
            if (StringUtils.hasText(kubeConfigPath)) {
                log.info("Using Kubernetes config from: {}", kubeConfigPath);
                client = loadConfigFromFile(kubeConfigPath);
            } else {
                
                log.info("Using default Kubernetes config");
                client = Config.defaultClient();
                
            }
            
            Configuration.setDefaultApiClient(client);
            api = new CoreV1Api();
            log.info("Kubernetes client initialized successfully");
        }
        catch (Exception e) 
        {
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


//    public String createLabPod(Lab lab) throws Exception {
//     try {
//         log.info("Create labpod for lab id {}", lab.getId());
//         V1Pod pod = buildLabPod(lab);
        
//         // Log trước khi gọi API
//         log.info("About to call createNamespacedPod API");
        
//         V1Pod createdPod = api.createNamespacedPod(namespace, pod, null, null, null, null);
//         String podName = createdPod.getMetadata().getName();
//         log.info("Successfully created pod: {} for lab: {}", podName, lab.getId());
        
//         return podName;
//     } catch (Exception e) {
//         // In tất cả thông tin có thể
//         System.err.println("Exception caught: " + e.getClass().getName());
//         System.err.println("Exception message: " + e.getMessage());
//         System.err.println("Exception toString: " + e.toString());
//         e.printStackTrace(); // In stack trace ra console
        
//         log.error("Exception class: {}", e.getClass().getName());
//         log.error("Exception message: {}", e.getMessage());
//         log.error("Full exception: ", e);
        
//         throw new Exception("Failed to create lab pod: " + e.toString(), e);
//     }
// }
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
            // Xử lý chi tiết ApiException
            log.error("=== Kubernetes API Exception Details ===");
            log.error("HTTP Status Code: {}", e.getCode());
            log.error("Response Body: {}", e.getResponseBody());
            log.error("Response Headers: {}", e.getResponseHeaders());
            log.error("Message: {}", e.getMessage());
            
            System.err.println("=== DEBUG API Exception ===");
            System.err.println("Status Code: " + e.getCode());
            System.err.println("Response Body: " + e.getResponseBody());
            System.err.println("Response Headers: " + e.getResponseHeaders());
            
            // Phân tích lỗi phổ biến và đưa ra gợi ý
         
            
            throw new Exception("Kubernetes API Error [" + e.getCode() + "]: " , e);
        } catch (Exception e) {
            log.error("General exception creating pod: ", e);
            System.err.println("General Exception: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();
            
            throw new Exception("Failed to create lab pod: " + e.getMessage(), e);
        }
    }
    private V1Pod buildLabPod(Lab lab) {
        System.out.println("It is in buidlabpod");
       String  nameLab = "lab-" + lab.getId() + "-" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        log.info("Creating pod with name: {}", nameLab);
        
        // Tạo metadata cho pod
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
        
        // CPU và Memory requests
        requests.put("cpu", new io.kubernetes.client.custom.Quantity("100m"));
        requests.put("memory", new io.kubernetes.client.custom.Quantity("128Mi"));
        
        // CPU và Memory limits
        limits.put("cpu", new io.kubernetes.client.custom.Quantity("500m"));
        limits.put("memory", new io.kubernetes.client.custom.Quantity("512Mi"));

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
}

