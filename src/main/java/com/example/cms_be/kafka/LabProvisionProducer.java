package com.example.cms_be.kafka;
import com.example.cms_be.dto.kafka.LabProvisionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LabProvisionProducer {
    
    private final KafkaTemplate<String, LabProvisionRequest> kafkaTemplate;
    private static final String TOPIC = "lab-provision-requests";
    
    public void sendProvisionRequest(LabProvisionRequest request) {
        log.info("Sending provision request for session: {}", request.getSessionId());
        kafkaTemplate.send(TOPIC, request.getSessionId().toString(), request);
    }
}