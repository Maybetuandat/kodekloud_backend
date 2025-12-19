package com.example.cms_be.config;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

@Configuration
@Slf4j
public class KubernetesClientConfig {

    @Value("${kubernetes.config.file.path:}")
    private String kubeConfigPath;

    @Bean("apiClient")
    @Primary
    public ApiClient apiClient() throws IOException {
        log.info("Creating Kubernetes ApiClient bean...");
        ApiClient client;
        
        if (StringUtils.hasText(kubeConfigPath)) {
            log.info("Using Kubernetes config from: {}", kubeConfigPath);
            File configFile = new File(kubeConfigPath);
            if (!configFile.exists()) {
                throw new IOException("Kubernetes config file not found at: " + kubeConfigPath);
            }
            try (FileReader reader = new FileReader(configFile)) {
                KubeConfig kubeConfig = KubeConfig.loadKubeConfig(reader);
                client = ClientBuilder.kubeconfig(kubeConfig).build();
            }
        } else {
            log.info("Using default in-cluster Kubernetes config");
            client = Config.defaultClient();
        }
        
        io.kubernetes.client.openapi.Configuration.setDefaultApiClient(client);
        log.info("Kubernetes ApiClient bean created successfully.");
        return client;
    }

    @Bean
    public CoreV1Api coreV1Api(ApiClient apiClient) {
        return new CoreV1Api(apiClient);
    }

    @Bean
    public CustomObjectsApi customObjectsApi(ApiClient apiClient) {
        return new CustomObjectsApi(apiClient);
    }

    @Bean("longTimeoutApiClient")
    public ApiClient longTimeoutApiClient(ApiClient defaultApiClient) throws IOException {
        log.info("Creating LONG TIMEOUT Kubernetes ApiClient bean...");
        ApiClient client;
        
        if (StringUtils.hasText(kubeConfigPath)) {
            try (FileReader reader = new FileReader(kubeConfigPath)) {
                client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(reader)).build();
            }
        } else {
            client = Config.defaultClient();
        }
        
        client.setReadTimeout(0);
        client.setWriteTimeout(0);
        client.setConnectTimeout(0);
        
        return client;
    }
}