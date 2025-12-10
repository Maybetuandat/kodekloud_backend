package com.example.cms_be.controller;

import com.example.cms_be.dto.CheckQuestionRequest;
import com.example.cms_be.dto.QuestionSubmissionStatus;
import com.example.cms_be.model.Answer;
import com.example.cms_be.model.Question;
import com.example.cms_be.model.Submission;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.service.*;
import jakarta.persistence.EntityNotFoundException;
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

    private final LabValidationService validationService;
    private final SubmissionService submissionService;
    private final LabSessionService labSessionService;
    private final QuestionService questionService;
    private final AnswerService answerService;

    @PostMapping("/{labSessionId}/check/{questionId}")
    public ResponseEntity<?> checkLabQuestion(
            @PathVariable Integer labSessionId,
            @PathVariable Integer questionId,
            @RequestBody(required = false) CheckQuestionRequest requestBody) {

        try {
            Integer userAnswerId = (requestBody != null) ? requestBody.userAnswer() : null;
            UserLabSession labSession = labSessionService.findById(labSessionId)
                    .orElseThrow(() -> new EntityNotFoundException("Lab Session not found: " + labSessionId));

            Question question = questionService.findById(questionId)
                    .orElseThrow(() -> new EntityNotFoundException("Question not found: " + questionId));

            Answer userAnswer = null;
            if (userAnswerId != null) {
                userAnswer = answerService.findById(userAnswerId)
                        .orElseThrow(() -> new EntityNotFoundException("Answer not found: " + userAnswerId));
            }
            Submission submission = Submission.builder()
                    .userLabSession(labSession)
                    .question(question)
                    .userAnswer(userAnswer)
                    .build();

            boolean isCorrect = validationService.validateQuestion(submission);
            submission.setCorrect(isCorrect);
            boolean isSubmit = submissionService.saveSubmission(submission);

            if (isCorrect && isSubmit) {
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

    @GetMapping("/{labSessionId}/status/{questionId}")
    public ResponseEntity<?> getQuestionStatus(
            @PathVariable Integer labSessionId,
            @PathVariable Integer questionId) {
        QuestionSubmissionStatus submissionStatus = submissionService.isSubmitted(labSessionId, questionId);

        return ResponseEntity.ok(submissionStatus);
    }
}
