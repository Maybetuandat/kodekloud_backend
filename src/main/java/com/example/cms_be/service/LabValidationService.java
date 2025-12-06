package com.example.cms_be.service;

import com.example.cms_be.dto.connection.ExecuteCommandResult;
import com.example.cms_be.model.Question;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.QuestionRepository;
import com.example.cms_be.repository.UserLabSessionRepository;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

@Service
@Slf4j
public class LabValidationService {

    private final UserLabSessionRepository userLabSessionRepository;
    private final QuestionRepository questionRepository;
    private final KubernetesDiscoveryService discoveryService;
    private final ApiClient apiClient;

    private final String defaultUsername = "ubuntu";
    private final String defaultPassword = "1234";

    public LabValidationService(
            UserLabSessionRepository userLabSessionRepository,
            QuestionRepository questionRepository,
            KubernetesDiscoveryService discoveryService,
            @Qualifier("longTimeoutApiClient") ApiClient apiClient) {
        this.userLabSessionRepository = userLabSessionRepository;
        this.questionRepository = questionRepository;
        this.discoveryService = discoveryService;
        this.apiClient = apiClient;
        this.apiClient.setReadTimeout(0);
    }

    @Transactional(readOnly = true)
    public boolean validateQuestion(Integer labSessionId, Integer questionId) throws ApiException {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy câu hỏi với ID: " + questionId));
        String checkCommand = question.getCheckCommand();
        if (checkCommand == null || checkCommand.isBlank())
            throw new IllegalStateException("This question does not have check cmd");

        UserLabSession session = userLabSessionRepository.findById(labSessionId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy UserLabSession với ID: " + labSessionId));

        String vmName = "vm-" + session.getId();
        String namespace = session.getLab().getNamespace();

        V1Pod pod;
        try {
            pod = discoveryService.waitForPodRunning(vmName, namespace, 10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        String podName = pod.getMetadata().getName();

        try {
            ExecuteCommandResult result = executeCommandViaTunnel(namespace, podName, checkCommand, 60);
            log.info("Check command '{}' resulted in exit code: {}", checkCommand, result.getExitCode());
            return result.getExitCode() == 0;

        } catch (Exception e) {
            log.error("Error executing check command for session {}: {}", labSessionId, e.getMessage(), e);
            return false;
        }
    }

    private ExecuteCommandResult executeCommandViaTunnel(String namespace, String podName, String command, int timeoutSecond) throws Exception {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        StringBuilder outputBuffer = new StringBuilder();
        int exitCode = -1;

        try {
            session = jsch.getSession(defaultUsername, "localhost", 2222);
            session.setPassword(defaultPassword);
            session.setConfig("StrictHostKeyChecking", "no");

            session.setSocketFactory(new SetupExecutionService.K8sTunnelSocketFactory(apiClient, namespace, podName));

            session.connect(15000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            channel.connect(10000);

            byte[] tmp = new byte[1024];
            long startTime = System.currentTimeMillis();

            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                    outputBuffer.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    if (in.available() > 0) continue;
                    exitCode = channel.getExitStatus();
                    break;
                }
                if (System.currentTimeMillis() - startTime > timeoutSecond * 1000L) {
                    throw new RuntimeException("Command timeout");
                }
                Thread.sleep(100);
            }
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }

        return new ExecuteCommandResult(exitCode, outputBuffer.toString().trim(), "");
    }
}