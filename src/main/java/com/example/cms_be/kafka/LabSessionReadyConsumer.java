
package com.example.cms_be.kafka;

import com.example.cms_be.dto.lab.LabSessionCleanupRequest;
import com.example.cms_be.dto.lab.LabSessionReadyEvent;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.UserLabSessionRepository;
import com.example.cms_be.service.UserLabSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class LabSessionReadyConsumer {

    private final UserLabSessionService userLabSessionService;
    private final UserLabSessionRepository userLabSessionRepository;
    private final LabSessionCleanupProducer cleanupProducer;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    @KafkaListener(
        topics = "lab-session-ready",
        groupId = "cms-backend-group",
        containerFactory = "labSessionReadyKafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeLabSessionReady(LabSessionReadyEvent event) {
        log.info("Received lab session ready event: labSessionId={}, vmName={}, podName={}",
            event.getLabSessionId(), event.getVmName(), event.getPodName());

        try {
            userLabSessionService.activateSession(
                event.getLabSessionId(),
                event.getPodName()
            );
            log.info("Successfully activated session: {}", event.getLabSessionId());

            UserLabSession session = userLabSessionRepository.findById(event.getLabSessionId())
                .orElse(null);

            if (session == null || session.getExpiresAt() == null) {
                log.warn("Cannot schedule cleanup for session {}: session or expiresAt is null", event.getLabSessionId());
                return;
            }

            String namespace = session.getLab() != null ? session.getLab().getNamespace() : "default";

            scheduleSessionCleanup(event, session.getExpiresAt(), namespace);

        } catch (Exception e) {
            log.error("Failed to activate session {}: {}", event.getLabSessionId(), e.getMessage(), e);
        }
    }

    private void scheduleSessionCleanup(LabSessionReadyEvent event, LocalDateTime expiresAt, String namespace) {
        long delayMinutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), expiresAt);

        if (delayMinutes <= 0) {
            log.warn("Session {} already expired, executing cleanup immediately", event.getLabSessionId());
            executeCleanup(event, namespace);
            return;
        }

        log.info("Scheduling cleanup task for session {} in {} minutes", event.getLabSessionId(), delayMinutes);

        scheduler.schedule(() -> {
            try {
                UserLabSession currentSession = userLabSessionRepository.findById(event.getLabSessionId())
                    .orElse(null);

                if (currentSession == null) {
                    log.warn("Session {} not found, skipping cleanup", event.getLabSessionId());
                    return;
                }

                if ("COMPLETED".equals(currentSession.getStatus())) {
                    log.info("Session {} already completed, skipping cleanup", event.getLabSessionId());
                    return;
                }

                currentSession.setStatus("COMPLETED");
                userLabSessionRepository.save(currentSession);

                executeCleanup(event, namespace);

            } catch (Exception e) {
                log.error("Error executing scheduled cleanup for session {}: {}",
                    event.getLabSessionId(), e.getMessage(), e);
            }
        }, delayMinutes, TimeUnit.MINUTES);
    }

    private void executeCleanup(LabSessionReadyEvent event, String namespace) {
        log.info("Executing cleanup for expired session: {}", event.getLabSessionId());

        LabSessionCleanupRequest cleanupRequest = LabSessionCleanupRequest.builder()
            .labSessionId(event.getLabSessionId())
            .vmName(event.getVmName())
            .namespace(namespace != null ? namespace : "default")
            .build();

        cleanupProducer.sendCleanupRequest(cleanupRequest);
        log.info("Sent cleanup request for expired session: {}", event.getLabSessionId());
    }
}