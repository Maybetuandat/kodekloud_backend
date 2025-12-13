package com.example.cms_be.kafka;

import com.example.cms_be.dto.kafka.LabProvisionResponse;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.UserLabSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class LabProvisionConsumer {
    
    private final UserLabSessionRepository userLabSessionRepository;
    
    @KafkaListener(topics = "lab-provision-responses", groupId = "cms-backend")
    public void consumeProvisionResponse(LabProvisionResponse response) {
        log.info("Received provision response for session: {}", response.getSessionId());
        
        userLabSessionRepository.findById(response.getSessionId()).ifPresent(session -> {
            session.setStatus(response.getStatus());
            session.setPodName(response.getPodName());
            
            if ("READY".equals(response.getStatus()) || "SETTING_UP".equals(response.getStatus())) {
                session.setSetupCompletedAt(LocalDateTime.now());
            }
            
            userLabSessionRepository.save(session);
            log.info("Updated session {} to status: {}", response.getSessionId(), response.getStatus());
        });
    }
}