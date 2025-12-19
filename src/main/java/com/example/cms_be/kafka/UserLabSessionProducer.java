package com.example.cms_be.kafka;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.cms_be.dto.lab.UserLabSessionRequest;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserLabSessionProducer {
    
    private final KafkaTemplate<String, UserLabSessionRequest> userLabSessionKafkaTemplate;
    private static final String TOPIC = "user-lab-session-requests";
    
    public void sendUserLabSessionRequest(UserLabSessionRequest request) {
        log.info("Sending user lab session request to Kafka: labSessionId={}, vmName={}", 
            request.getLabSessionId(), request.getVmName());
        userLabSessionKafkaTemplate.send(TOPIC, request.getVmName(), request);
    }
}
