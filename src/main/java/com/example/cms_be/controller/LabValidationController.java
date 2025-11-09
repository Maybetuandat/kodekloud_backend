package com.example.cms_be.controller;

import com.example.cms_be.service.LabValidationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api/lab-validation")
@RequiredArgsConstructor
public class LabValidationController {

    private final LabValidationService validationService;

    @PostMapping("/{labSessionId}/check/{questionId}")
    public ResponseEntity<?> checkLabQuestion(
            @PathVariable Integer labSessionId,
            @PathVariable Integer questionId) {

        try {
            boolean isCorrect = validationService.validateQuestion(labSessionId, questionId);

            if (isCorrect) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Chúc mừng! Câu trả lời chính xác."
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Chưa chính xác. Vui lòng kiểm tra lại các bước."
                ));
            }
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to validate question {}: {}", questionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Lỗi hệ thống khi kiểm tra câu trả lời."));
        }
    }
}
