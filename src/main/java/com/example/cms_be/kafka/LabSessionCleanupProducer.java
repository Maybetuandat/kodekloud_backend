package com.example.cms_be.kafka;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.cms_be.dto.lab.LabSessionCleanupRequest;

@Service
@Slf4j
@RequiredArgsConstructor
public class LabSessionCleanupProducer {

    private final KafkaTemplate<String, LabSessionCleanupRequest> cleanupRequestKafkaTemplate;

    private static final String TOPIC = "lab-session-cleanup-requests";

    public void sendCleanupRequest(LabSessionCleanupRequest request) {
        try {
            cleanupRequestKafkaTemplate.send(TOPIC, request);
            log.info("Sent cleanup request: labSessionId={}, vmName={}, namespace={}",
                    request.getLabSessionId(), request.getVmName(), request.getNamespace());
        } catch (Exception e) {
            log.error("Failed to send cleanup request for labSessionId={}: {}",
                    request.getLabSessionId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send cleanup request", e);
        }
    }
}