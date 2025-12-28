
package com.example.cms_be.controller;

import com.example.cms_be.dto.CheckQuestionRequest;
import com.example.cms_be.model.Submission;
import com.example.cms_be.service.SubmissionService;

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

    private final SubmissionService submissionService;

    @PostMapping("/{labSessionId}/check/{questionId}")
    public ResponseEntity<?> checkLabQuestion(
            @PathVariable Integer labSessionId,
            @PathVariable Integer questionId,
            @RequestBody(required = false) CheckQuestionRequest requestBody) {

        try {
            Integer userAnswerId = (requestBody != null) ? requestBody.userAnswer() : null;
            
            submissionService.submitQuestion(labSessionId, questionId, userAnswerId);

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
        
          String status = submissionService.getQuestionSubmissionStatus(labSessionId, questionId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", status
            ));
    }
}