
package com.example.cms_be.controller;

import com.example.cms_be.dto.CheckQuestionRequest;
import com.example.cms_be.model.Submission;
import com.example.cms_be.service.LabValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@Slf4j
@RequestMapping("/api/lab-validation")
@RequiredArgsConstructor
public class LabValidationController {

    private final LabValidationService labValidationService;

    @PostMapping("/{labSessionId}/check/{questionId}")
    public ResponseEntity<?> checkLabQuestion(
            @PathVariable Integer labSessionId,
            @PathVariable Integer questionId,
            @RequestBody(required = false) CheckQuestionRequest requestBody) {

        try {
            Integer userAnswerId = (requestBody != null) ? requestBody.userAnswer() : null;
            
            labValidationService.submitValidationRequest(labSessionId, questionId, userAnswerId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Đang kiểm tra câu trả lời..."
            ));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to validate question {}: {}", questionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Lỗi hệ thống khi kiểm tra câu trả lời."));
        }
    }

    @GetMapping("/{labSessionId}/status/{questionId}")
    public ResponseEntity<?> getQuestionStatus(
            @PathVariable Integer labSessionId,
            @PathVariable Integer questionId) {
        
        boolean isPending = labValidationService.isPending(labSessionId, questionId);
        Optional<Submission> latestSubmission = labValidationService.getLatestSubmission(labSessionId, questionId);

        return ResponseEntity.ok(Map.of(
                "isPending", isPending,
                "hasSubmission", latestSubmission.isPresent(),
                "isCorrect", latestSubmission.map(Submission::isCorrect).orElse(false),
                "status", latestSubmission.map(Submission::getStatus).orElse("NOT_SUBMITTED")
        ));
    }
}