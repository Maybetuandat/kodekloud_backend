package com.example.cms_be.kafka;

import com.example.cms_be.dto.lab.ValidationResponse;
import com.example.cms_be.service.SubmissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ValidationConsumer {

    private final ObjectMapper objectMapper;
    private final SubmissionService submissionService;

    @KafkaListener(topics = "lab-validation-responses", groupId = "cms-validation-group")
    public void consumeValidationResponse(String message) {
        try {
            ValidationResponse response = objectMapper.readValue(message, ValidationResponse.class);
            log.info("📥 Received validation response: labSessionId={}, questionId={}, isCorrect={}",
                    response.getLabSessionId(), response.getQuestionId(), response.isCorrect());

            submissionService.processValidationResponse(response);

        } catch (Exception e) {
            log.error("❌ Failed to process validation response", e);
        }
    }
}