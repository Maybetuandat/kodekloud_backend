package com.example.cms_be.service;

import com.example.cms_be.model.Lab;
import com.example.cms_be.model.User;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.UserLabSessionRepository;
import com.google.gson.Gson;
import io.kubernetes.client.Exec;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.Yaml;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class VMService {
    private static final String KUBEVIRT_GROUP = "kubevirt.io";
    private static final String KUBEVIRT_VERSION = "v1";
    private static final String KUBEVIRT_PLURAL_VM = "virtualmachines";

    private static final String CDI_GROUP = "cdi.kubevirt.io";
    private static final String CDI_VERSION = "v1beta1";
    private static final String CDI_PLURAL_DV = "datavolumes";

    private CustomObjectsApi customApi;
    private ApiClient client;
    private CoreV1Api coreApi;
    private Exec exec;

    private UserLabSessionRepository userLabSessionRepository;

    @Value("${kubernetes.namespace:default}")
    private String namespace;

    @Value("${kubernetes.config.file.path:}")
    private String kubeConfigPath;

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
        return coreApi;
    }

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
            coreApi = new CoreV1Api();
            customApi = new CustomObjectsApi(client); // Khởi tạo CustomObjectsApi
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

    @Async
    public void provisionVmForSession(UserLabSession session) throws ApiException, IOException {
        try {
            log.info("Starting virtual machine creation process for '{}'...", session.getLab().getName());

            Lab lab = session.getLab();
            String vmName = "vm-" + lab.getId();

            System.out.println(vmName + "\n" + lab.getNamespace() + "\n" + lab.getBaseImage() + "\n" + lab.getStorage() + "\n" + lab.getMemory());

            createDataVolumeFromTemplate(vmName, lab.getNamespace(), lab.getBaseImage(), lab.getStorage());
            createVirtualMachineFromTemplate(vmName, lab.getNamespace(), lab.getMemory());
            createSshServiceForVM(vmName, lab.getNamespace());

            log.info("Successfully completed virtual machine creation process for '{}'.", lab.getName());
        } catch (ApiException e) {
            log.error("!!! Kubernetes API Error for session {}. Status Code: {}. Response Body: {}",
                    session.getId(), e.getCode(), e.getResponseBody(), e);

            log.error("Updating session {} status to FAILED.", session.getId());
            session.setStatus("FAILED");
            userLabSessionRepository.save(session);

        } catch (Exception e) {
            log.error("Unexpected error provisioning VM for session {}", session.getId(), e);

            log.error("Updating session {} status to FAILED.", session.getId());
            session.setStatus("FAILED");
            userLabSessionRepository.save(session);
        }
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

    private void createDataVolumeFromTemplate(String name, String namespace, String imageUrl, String storage) throws IOException, ApiException {
        Map<String, String> values = Map.of(
                "NAME", name,
                "NAMESPACE", namespace,
                "IMAGE_URL", imageUrl,
                "STORAGE", storage
        );
        String dataVolumeYaml = loadAndRenderTemplate("templates/datavolume-template.yaml", values);
        @SuppressWarnings("rawtypes")
        Map dataVolumeBody = Yaml.loadAs(dataVolumeYaml, Map.class);

        log.info("Creating DataVolume '{}'...", name);
        customApi.createNamespacedCustomObject(CDI_GROUP, CDI_VERSION, namespace, CDI_PLURAL_DV, dataVolumeBody, null, null, null);
        log.info("DataVolume '{}' created. Image import is in progress.", name);
    }

    private void createVirtualMachineFromTemplate(String name, String namespace, String memory) throws IOException, ApiException {
        Map<String, String> values = Map.of(
                "NAME", name,
                "NAMESPACE", namespace,
                "MEMORY", memory
        );
        String virtualMachineYaml = loadAndRenderTemplate("templates/vm-template.yaml", values);
        @SuppressWarnings("rawtypes")
        Map vmBody = Yaml.loadAs(virtualMachineYaml, Map.class);

        log.info("Creating VirtualMachine '{}'...", name);
        customApi.createNamespacedCustomObject(KUBEVIRT_GROUP, KUBEVIRT_VERSION, namespace, KUBEVIRT_PLURAL_VM, vmBody, null, null, null);
        log.info("VirtualMachine '{}' definition created successfully.", name);
    }

    private void createSshServiceForVM(String name, String namespace) throws ApiException {
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
                                .port(22)
                                .targetPort(new IntOrString(22))));

        log.info("Creating Service '{}'...", serviceName);
        coreApi.createNamespacedService(namespace, serviceBody, null, null, null, null);
        log.info("Service '{}' created successfully.", serviceName);
    }

    @Async
    public void excuteSetupStep(Lab lab, User user) {

    }
}