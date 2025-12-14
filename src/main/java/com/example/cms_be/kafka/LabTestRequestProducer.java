package com.example.cms_be.kafka;

import com.example.cms_be.dto.lab.LabTestRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LabTestRequestProducer {
    
    private final KafkaTemplate<String, LabTestRequest> kafkaTemplate;
    private static final String TOPIC = "lab-test-requests";
    
    public void sendLabTestRequest(LabTestRequest request) {
        log.info("Sending lab test request: testVmName={}", request.getTestVmName());
        kafkaTemplate.send(TOPIC, request.getTestVmName(), request);
    }
}