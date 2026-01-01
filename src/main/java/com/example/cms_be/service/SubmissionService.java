package com.example.cms_be.service;

import com.example.cms_be.constant.QuestionType;
import com.example.cms_be.constant.SubmissionStatus;
import com.example.cms_be.dto.lab.ValidationRequest;
import com.example.cms_be.dto.lab.ValidationResponse;
import com.example.cms_be.kafka.ValidationProducer;
import com.example.cms_be.model.Answer;
import com.example.cms_be.model.Question;
import com.example.cms_be.model.Submission;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.AnswerRepository;
import com.example.cms_be.repository.QuestionRepository;
import com.example.cms_be.repository.SubmissionRepository;
import com.example.cms_be.repository.UserLabSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubmissionService {
    private final SubmissionRepository submissionRepository;
    private final UserLabSessionRepository userLabSessionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final ValidationProducer validationProducer;

    @Transactional
    public void submitQuestion(Integer labSessionId, Integer questionId, Integer userAnswerId) {
        UserLabSession userLabSession = userLabSessionRepository.findById(labSessionId)
                .orElseThrow(() -> new IllegalStateException("Lab session not found"));
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalStateException("Question not found"));

        Optional<Submission> existingSubmission = submissionRepository
                .findByUserLabSessionIdAndQuestionId(labSessionId, questionId);

        if (existingSubmission.isPresent()) {
            throw new IllegalStateException("Bạn đã nộp bài cho câu hỏi này rồi.");
        }

        Submission submission = new Submission();
        submission.setUserLabSession(userLabSession);
        submission.setQuestion(question);

        if (question.getTypeQuestion() == QuestionType.NON_CHECK) {
            if (userAnswerId == null) {
                throw new IllegalStateException("Câu hỏi này yêu cầu phải chọn đáp án.");
            }
            Answer userAnswer = answerRepository.findById(userAnswerId)
                    .orElseThrow(() -> new IllegalStateException("Answer not found"));
            submission.setUserAnswer(userAnswer);

            if (userAnswer.getIsRightAns()) {
                submission.setStatus(SubmissionStatus.SUBMISSION_SUCCESS);
                submission.setCorrect(true);
            } else {
                submission.setStatus(SubmissionStatus.SUBMISSION_FAILED);
                submission.setCorrect(false);
            }
        } else if (question.getTypeQuestion() == QuestionType.CHECK_OUTPUT) {


              log.info("Đã gọi đến hàm checkout");
            submission.setStatus(SubmissionStatus.SUBMISSION_PENDING);
            submission.setCorrect(false);
            submissionRepository.save(submission);

            sendValidationRequest(userLabSession, question, submission.getId());
            return;
        }

        submissionRepository.save(submission);
    }

    private void sendValidationRequest(UserLabSession userLabSession, Question question, Integer submissionId) {
        String vmName = "vm-" + userLabSession.getId();
        String namespace = userLabSession.getLab().getNamespace();
        String podName = userLabSession.getPodName();

        if (podName == null || podName.isEmpty()) {
            podName = vmName + "-0";
        }

        ValidationRequest request = ValidationRequest.builder()
                .labSessionId(userLabSession.getId())
                .questionId(question.getId())
                .userAnswerId(submissionId)
                .vmName(vmName)
                .namespace(namespace)
                .podName(podName)
                .validationCommand(question.getCheckCommand())
                .build();

        log.info("📤 Sending validation request: labSessionId={}, questionId={}, command={}",
                userLabSession.getId(), question.getId(), question.getCheckCommand());

        validationProducer.sendValidationRequest(request);
    }

    @Transactional
    public void processValidationResponse(ValidationResponse response) {
        log.info("📥 Processing validation response: labSessionId={}, questionId={}, isCorrect={}",
                response.labSessionId(), response.questionId(), response.isCorrect());

        Optional<Submission> submissionOpt = submissionRepository
                .findByLabSessionAndQuestionAndStatus(
                        response.labSessionId(),
                        response.questionId(),
                        SubmissionStatus.SUBMISSION_PENDING
                );

        if (submissionOpt.isEmpty()) {
            log.warn("⚠️ No pending submission found for labSessionId={}, questionId={}",
                    response.labSessionId(), response.questionId());
            return;
        }

        Submission submission = submissionOpt.get();

        if (response.isCorrect()) {
            submission.setStatus(SubmissionStatus.SUBMISSION_SUCCESS);
            submission.setCorrect(true);
        } else {
            submission.setStatus(SubmissionStatus.SUBMISSION_FAILED);
            submission.setCorrect(false);
        }

        submissionRepository.save(submission);

        log.info("✅ Submission updated: id={}, status={}, isCorrect={}",
                submission.getId(), submission.getStatus(), submission.isCorrect());
    }

    public String getQuestionSubmissionStatus(Integer labSessionId, Integer questionId) {
        Optional<Submission> latestSubmission = submissionRepository
                .findByUserLabSessionIdAndQuestionId(labSessionId, questionId);
        return latestSubmission.map(Submission::getStatus).orElse(SubmissionStatus.NOT_SUBMISSION);
    }
}