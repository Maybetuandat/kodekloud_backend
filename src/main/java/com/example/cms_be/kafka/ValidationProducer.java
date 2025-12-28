package com.example.cms_be.kafka;

import com.example.cms_be.dto.lab.ValidationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ValidationProducer {
    
    private final KafkaTemplate<String, ValidationRequest> validationKafkaTemplate;
    
    private static final String TOPIC = "lab-validation-requests";
    
    public void sendValidationRequest(ValidationRequest request) {
        try {
            validationKafkaTemplate.send(TOPIC, request);
            log.info("📤 Sent validation request: labSessionId={}, questionId={}", 
                request.labSessionId(), request.questionId());
        } catch (Exception e) {
            log.error("❌ Failed to send validation request", e);
            throw new RuntimeException("Failed to send validation request", e);
        }
    }
}