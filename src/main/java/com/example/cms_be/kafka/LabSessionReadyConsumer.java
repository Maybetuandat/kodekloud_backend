package com.example.cms_be.kafka;

import com.example.cms_be.dto.lab.LabSessionReadyEvent;
import com.example.cms_be.service.UserLabSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LabSessionReadyConsumer {

    private final UserLabSessionService userLabSessionService;

    @KafkaListener(
        topics = "lab-session-ready",
        groupId = "cms-backend-group",
        containerFactory = "labSessionReadyKafkaListenerContainerFactory"
    )
    public void consumeLabSessionReady(LabSessionReadyEvent event) {
        log.info("Received lab session ready event: labSessionId={}, vmName={}, podName={}",
            event.getLabSessionId(), event.getVmName(), event.getPodName());

        try {
            userLabSessionService.activateSession(
                event.getLabSessionId(),
                event.getPodName()
            );
            log.info("Successfully activated session: {}", event.getLabSessionId());
        } catch (Exception e) {
            log.error("Failed to activate session {}: {}", event.getLabSessionId(), e.getMessage(), e);
        }
    }
}