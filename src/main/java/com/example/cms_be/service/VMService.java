package com.example.cms_be.service;

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
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class VMService {
    private CoreV1Api api;
    private ApiClient client;
    private Exec exec;

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
        return api;
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


    public List<Map<String, String>> getAllPods() throws ApiException {
        try {
            V1PodList podList = api.listPodForAllNamespaces(
                    null, null, null, null, null, null, null, null, null, null);

            List<Map<String, String>> simplifiedPods = new ArrayList<>();
            for (V1Pod item : podList.getItems()) {
                Map<String, String> podInfo = new HashMap<>();

                if (item.getMetadata() != null) {
                    podInfo.put("name", item.getMetadata().getName());
                    podInfo.put("namespace", item.getMetadata().getNamespace());
                }
                if (item.getStatus() != null) {
                    podInfo.put("status", item.getStatus().getPhase());
                    podInfo.put("ip", item.getStatus().getPodIP());
                }
                simplifiedPods.add(podInfo);
            }
            return simplifiedPods;

        } catch (ApiException e) {
            // Log lỗi chi tiết hơn bằng cách log cả response body
            log.error("Failed to get all pods: {}, response body: {}", e.getMessage(), e.getResponseBody());
            throw new ApiException("Failed to get all pods: " + e.getMessage());
        }
    }

    public void createVirtualMachine(
            String name, String namespace, String imageUrl, String storage, String memory) throws ApiException {

        CustomObjectsApi customApi = new CustomObjectsApi(this.client);
        Gson gson = new Gson();

        // === 1. TẠO DATA VOLUME (từ file ubuntu-pvc.yaml) ===
        String dataVolumeJson = String.format("""
        {
            "apiVersion": "cdi.kubevirt.io/v1beta1",
            "kind": "DataVolume",
            "metadata": {
                "name": "%s",
                "namespace": "%s"
            },
            "spec": {
                "source": {
                    "http": {
                        "url": "%s"
                    }
                },
                "pvc": {
                    "accessModes": ["ReadWriteOnce"],
                    "resources": {
                        "requests": {
                            "storage": "%s"
                        }
                    }
                }
            }
        }
        """, name, namespace, imageUrl, storage);

        Object dataVolumeBody = gson.fromJson(dataVolumeJson, Object.class);

        log.info("Creating DataVolume '{}' in namespace '{}'...", name, namespace);
        customApi.createNamespacedCustomObject(
                "cdi.kubevirt.io",   // group
                "v1beta1",           // version
                namespace,           // namespace
                "datavolumes",       // plural
                dataVolumeBody,      // body
                null, null, null);
        log.info("DataVolume '{}' created. Image import is in progress.", name);

        // === 2. TẠO VIRTUAL MACHINE (từ file ubuntu-vm.yaml) ===
        // Lưu ý: User và password đang được hardcode như trong file YAML của bạn
        String virtualMachineJson = String.format("""
        {
            "apiVersion": "kubevirt.io/v1",
            "kind": "VirtualMachine",
            "metadata": {
                "name": "%s",
                "namespace": "%s"
            },
            "spec": {
                "runStrategy": "Always",
                "template": {
                    "metadata": {
                        "labels": {
                            "app": "%s"
                        }
                    },
                    "spec": {
                        "domain": {
                            "devices": {
                                "disks": [
                                    {"name": "rootdisk", "disk": {"bus": "virtio"}},
                                    {"name": "cloudinitdisk", "cdrom": {"bus": "sata"}}
                                ],
                                "interfaces": [
                                    {"name": "default", "masquerade": {}}
                                ]
                            },
                            "resources": {
                                "requests": {
                                    "memory": "%s"
                                }
                            }
                        },
                        "networks": [
                            {"name": "default", "pod": {}}
                        ],
                        "volumes": [
                            {
                                "name": "rootdisk",
                                "persistentVolumeClaim": {
                                    "claimName": "%s"
                                }
                            },
                            {
                                "name": "cloudinitdisk",
                                "cloudInitNoCloud": {
                                    "userData": "#cloud-config\\nuser: ubuntu\\npassword: 1234\\nchpasswd: { expire: false }\\nssh_pwauth: true"
                                }
                            }
                        ]
                    }
                }
            }
        }
        """, name, namespace, name, memory, name);

        Object vmBody = gson.fromJson(virtualMachineJson, Object.class);

        log.info("Creating VirtualMachine '{}' in namespace '{}'...", name, namespace);

        customApi.createNamespacedCustomObject(
                "kubevirt.io",       // group
                "v1",                // version
                namespace,           // namespace
                "virtualmachines",   // plural
                vmBody,              // body
                null, null, null);
        log.info("VirtualMachine '{}' definition created successfully.", name);

        String serviceName = "ssh-" + name;
        V1Service serviceBody = new V1Service()
                .apiVersion("v1")
                .kind("Service")
                .metadata(new V1ObjectMeta()
                        .name(serviceName)
                        .namespace(namespace))
                .spec(new V1ServiceSpec()
                        .type("NodePort")
                        .putSelectorItem("vm.kubevirt.io/name", name)
                        .addPortsItem(new V1ServicePort()
                                .protocol("TCP")
                                .port(22)
                                .targetPort(new IntOrString(22))));

        // Sử dụng CoreV1Api để tạo service
        this.api.createNamespacedService(namespace, serviceBody, null, null, null, null);

        log.info("Service '{}' created successfully.", serviceName);
    }

}
