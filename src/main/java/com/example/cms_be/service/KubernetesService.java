package com.example.cms_be.service;

import org.springframework.stereotype.Service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import io.kubernetes.client.Exec;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


import java.io.File;
import java.io.FileReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.util.StringUtils;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
@Service
@Slf4j
public class KubernetesService {


    private CoreV1Api api;
    private ApiClient client;

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



    //  public void performHealthCheck() {
    //     try {
    //         log.info("=== Kubernetes Health Check Started ===");
            
    //         // Test 1: Check if we can connect to the cluster
    //         log.info("Testing cluster connectivity...");
    //         V1NodeList nodeList = api.listNode(null, null, null, null, null, null, null, null, null, null);
    //         log.info("✅ Successfully connected to Kubernetes cluster");
    //         log.info("Found {} nodes in the cluster", nodeList.getItems());
            
    //     }
    //     catch (Exception e) {
    //         log.error("❌ Kubernetes health check failed: {}", e.getMessage());
    //         throw new RuntimeException("Kubernetes health check failed", e);
    //     }
    // }



}

