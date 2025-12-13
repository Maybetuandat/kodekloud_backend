package com.example.cms_be.service;

import com.example.cms_be.dto.QuestionSubmissionStatus;
import com.example.cms_be.model.Submission;
import com.example.cms_be.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubmissionService {
    private final SubmissionRepository submissionRepository;

    public Optional<Submission> findById(Integer id) {
        return submissionRepository.findById(id);
    }

    public boolean saveSubmission(Submission submission) {
        Optional<Submission> existSubmission = submissionRepository.findByUserLabSessionIdAndQuestionId(
                submission.getUserLabSession().getId(),
                submission.getQuestion().getId()
        );
        if(existSubmission.isPresent()) {
            return false;
        }
        submissionRepository.save(submission);
        return true;
    }

    public QuestionSubmissionStatus isSubmitted(Integer labSessionId, Integer questionId) {
        Optional<Submission> submissionOpt = submissionRepository.findByUserLabSessionIdAndQuestionId(labSessionId, questionId);

        if (submissionOpt.isPresent()) {
            Submission sub = submissionOpt.get();
            Integer userAnswerId = -1;
            if(sub.getUserAnswer() != null) {
                userAnswerId = sub.getUserAnswer().getId();
            }
            boolean isCorrect = sub.isCorrect();
            return new QuestionSubmissionStatus(true, isCorrect, userAnswerId);
        } else {
            return new QuestionSubmissionStatus(false, false, null);
        }
    }

    public List<Submission> findByUserLabSession(Integer id) {
        return submissionRepository.findAllByUserLabSessionId(id);
    }
}
