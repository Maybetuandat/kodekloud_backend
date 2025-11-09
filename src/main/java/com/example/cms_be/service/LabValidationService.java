package com.example.cms_be.service;

import com.example.cms_be.model.Question;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.LabRepository;
import com.example.cms_be.repository.QuestionRepository;
import com.example.cms_be.repository.UserLabSessionRepository;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.kubernetes.client.openapi.ApiException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class LabValidationService {

    private final UserLabSessionRepository userLabSessionRepository;
    private final QuestionRepository questionRepository;
    private final KubernetesDiscoveryService discoveryService;

    @Transactional(readOnly = true)
    public boolean validateQuestion(Integer labSessionId, Integer questionId) throws ApiException {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy câu hỏi với ID: " + questionId));
        String checkCommand = question.getCheckCommand();
        if(checkCommand == null || checkCommand.isBlank())
            throw new IllegalStateException("This question does not have check cmd");

        UserLabSession session = userLabSessionRepository.findById(labSessionId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy UserLabSession với ID: " + labSessionId));
        String vmName = "vm-" + session.getId();
        String namespace = session.getLab().getNamespace();
        KubernetesDiscoveryService.SshConnectionDetails details = discoveryService.getExternalSshDetails(vmName, namespace);
        try {
            KubernetesService.CommandResult result = executeCommandViaSsh(details, checkCommand, 60);
            log.info("Check command for question {} resulted in exit code: {}", question.getQuestion(), result.getExitCode());

            return result.getExitCode() == 0;

        } catch (Exception e) {
            log.error("Error executing check command for session {}: {}", labSessionId, e.getMessage(), e);
            return false;
        }
    }

    private KubernetesService.CommandResult executeCommandViaSsh(KubernetesDiscoveryService.SshConnectionDetails details, String command, int timeoutSecond) throws Exception {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        StringBuilder outputBuffer = new StringBuilder();
        int exitCode = -1;
        try {
            session = jsch.getSession("ubuntu", details.host(), details.port());
            session.setPassword("1234");
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(15000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setPty(true);

            InputStream in = channel.getInputStream();
            channel.connect(10000); // 10s channel connection timeout

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                    outputBuffer.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    exitCode = channel.getExitStatus();
                    break;
                }
                Thread.sleep(100);
            }
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
        return new KubernetesService.CommandResult(exitCode, outputBuffer.toString().trim(), "");
    }
}
