package com.example.cms_be.service;

import com.example.cms_be.model.Lab;
import com.example.cms_be.model.SetupStep;
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
import java.net.InetSocketAddress;
import java.net.Socket;
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

    private final CustomObjectsApi customApi;
    private final ApiClient client;
    private final CoreV1Api coreApi;
    private Exec exec;

    private static final int defaultSshPort = 22;

    private final UserLabSessionRepository userLabSessionRepository;

    private final SetupExecutionService setupExecutionService;

    @Getter
    @Value("${kubernetes.namespace:default}")
    private String namespace;

    @Value("${app.execution-environment}")
    private String executionEnvironment;

    public void createKubernetesResourcesForSession(UserLabSession session) throws IOException, ApiException {
        String vmName = "vm-" + session.getId();
        String namespace = session.getLab().getNamespace();
        Lab lab = session.getLab();

        createDataVolumeFromTemplate(vmName, namespace, lab.getBaseImage(), lab.getStorage());
        createVirtualMachineFromTemplate(vmName, namespace, lab.getMemory());
        createSshServiceForVM(vmName, namespace);
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
                                .port(defaultSshPort)
                                .targetPort(new IntOrString(defaultSshPort))));

        log.info("Creating Service '{}'...", serviceName);
        coreApi.createNamespacedService(namespace, serviceBody, null, null, null, null);
        log.info("Service '{}' created successfully.", serviceName);
    }


    private void updateSessionStatus(UserLabSession session, String status) {
        log.info("Updating session {} status from '{}' to '{}'.", session.getId(), session.getStatus(), status);
        session.setStatus(status);
        userLabSessionRepository.save(session);
    }
}