// cms-be/src/main/java/com/example/cms_be/service/LabValidationService.java
package com.example.cms_be.service;

import com.example.cms_be.dto.ValidationRequest;
import com.example.cms_be.dto.ValidationResponse;
import com.example.cms_be.kafka.ValidationProducer;
import com.example.cms_be.model.*;
import com.example.cms_be.repository.SubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class LabValidationService {

    private final SubmissionRepository submissionRepository;
    private final LabSessionService labSessionService;
    private final QuestionService questionService;
    private final AnswerService answerService;
    private final ValidationProducer validationProducer;

    @Transactional
    public void submitValidationRequest(Integer labSessionId, Integer questionId, Integer userAnswerId) {
        
        Optional<Submission> pendingSubmission = submissionRepository
                .findByLabSessionAndQuestionAndStatus(labSessionId, questionId, ValidationStatus.PENDING);
        
        if (pendingSubmission.isPresent()) {
            throw new IllegalStateException("Câu hỏi đang được kiểm tra. Vui lòng đợi...");
        }

        UserLabSession labSession = labSessionService.findById(labSessionId)
                .orElseThrow(() -> new EntityNotFoundException("Lab Session not found: " + labSessionId));

        Question question = questionService.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found: " + questionId));

        if (question.getTypeQuestion() == null) {
            throw new IllegalStateException("Question type is not defined");
        }

        if (question.getTypeQuestion() == QuestionType.NON_CHECK) {
            handleNonCheckQuestion(labSession, question, userAnswerId);
        } else if (question.getTypeQuestion() == QuestionType.CHECK_OUTPUT) {
            handleCheckOutputQuestion(labSession, question, userAnswerId);
        } else {
            throw new IllegalStateException("Unknown question type: " + question.getTypeQuestion());
        }
    }

    private void handleNonCheckQuestion(UserLabSession labSession, Question question, Integer userAnswerId) {
        
        if (userAnswerId == null) {
            throw new IllegalArgumentException("User answer is required for non-check questions");
        }

        Answer userAnswer = answerService.findById(userAnswerId)
                .orElseThrow(() -> new EntityNotFoundException("Answer not found: " + userAnswerId));

        boolean isCorrect = userAnswer.isCorrect();

        Submission submission = Submission.builder()
                .userLabSession(labSession)
                .question(question)
                .userAnswer(userAnswer)
                .status(isCorrect ? ValidationStatus.VALIDATED : ValidationStatus.FAILED)
                .isCorrect(isCorrect)
                .build();

        submissionRepository.save(submission);
        
        log.info("✅ NON_CHECK question validated: labSessionId={}, questionId={}, isCorrect={}", 
            labSession.getId(), question.getId(), isCorrect);
    }

    private void handleCheckOutputQuestion(UserLabSession labSession, Question question, Integer userAnswerId) {
        
        Submission submission = Submission.builder()
                .userLabSession(labSession)
                .question(question)
                .status(ValidationStatus.PENDING)
                .isCorrect(false)
                .build();

        submission = submissionRepository.save(submission);
        log.info("💾 Saved PENDING submission: id={}, labSessionId={}, questionId={}", 
            submission.getId(), labSession.getId(), question.getId());

        ValidationRequest validationRequest = ValidationRequest.builder()
                .labSessionId(labSession.getId())
                .questionId(question.getId())
                .userAnswerId(userAnswerId)
                .vmName(labSession.getVmName())
                .namespace(labSession.getLab().getNamespace())
                .podName(labSession.getPodName())
                .validationCommand(question.getCheckCommand())
                .build();

        validationProducer.sendValidationRequest(validationRequest);
        log.info("📤 Sent validation request to Kafka: labSessionId={}, questionId={}", 
            labSession.getId(), question.getId());
    }

    @Transactional
    public void processValidationResponse(ValidationResponse response) {
        
        Optional<Submission> pendingSubmission = submissionRepository
                .findByLabSessionAndQuestionAndStatus(
                    response.labSessionId(), 
                    response.questionId(), 
                    ValidationStatus.PENDING
                );

        if (pendingSubmission.isEmpty()) {
            log.warn("⚠️ No PENDING submission found for labSessionId={}, questionId={}", 
                response.labSessionId(), response.questionId());
            return;
        }

        Submission submission = pendingSubmission.get();
        submission.setCorrect(response.isCorrect());
        submission.setStatus(response.isCorrect() ? ValidationStatus.VALIDATED : ValidationStatus.FAILED);
        
        submissionRepository.save(submission);
        
        log.info("✅ Updated submission: id={}, labSessionId={}, questionId={}, isCorrect={}, status={}", 
            submission.getId(), response.labSessionId(), response.questionId(), 
            response.isCorrect(), submission.getStatus());
    }

    public boolean isPending(Integer labSessionId, Integer questionId) {
        return submissionRepository
                .findByLabSessionAndQuestionAndStatus(labSessionId, questionId, ValidationStatus.PENDING)
                .isPresent();
    }

    public Optional<Submission> getLatestSubmission(Integer labSessionId, Integer questionId) {
        return submissionRepository.findLatestByLabSessionAndQuestion(labSessionId, questionId);
    }
}