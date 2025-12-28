package com.example.cms_be.service;
import com.example.cms_be.constant.QuestionType;
import com.example.cms_be.constant.SubmissionStatus;
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
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubmissionService {
    private final SubmissionRepository submissionRepository;
    private final UserLabSessionRepository userLabSessionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;

   
    public void submitQuestion(Integer labSessionId, Integer questionId, Integer userAnswerId) {
        UserLabSession userLabSession = userLabSessionRepository.findById(labSessionId)
                .orElseThrow(() -> new IllegalStateException("Lab session not found"));
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalStateException("Question not found"));
        Answer userAnwer = answerRepository.findById(userAnswerId)
                .orElseThrow(() -> new IllegalStateException("Answer not found"));
       Optional<Submission> existingSubmission = submissionRepository
                .findByUserLabSessionIdAndQuestionId(labSessionId, questionId);
        Submission submission;
        if (existingSubmission.isPresent())
        {
               throw new IllegalStateException("Bạn đã nộp bài cho câu hỏi này rồi.");
        }
        else
        {
            submission = new Submission();
            submission.setUserLabSession(userLabSession);
            submission.setQuestion(question);
            submission.setUserAnswer(userAnwer);
            if(question.getTypeQuestion() == QuestionType.NON_CHECK)
            {
                if(userAnwer.getIsRightAns())
                {
                    submission.setStatus(SubmissionStatus.SUBMISSION_SUCCESS);
                }
                else
                {
                    submission.setStatus(SubmissionStatus.SUBMISSION_FAILED);
                }
            }
       
             //handleCheckOutputQuestion(submission);
        }
        
        
        submissionRepository.save(submission);

    }
    public String getQuestionSubmissionStatus(Integer labSessionId, Integer questionId) {
        Optional<Submission> latestSubmission = submissionRepository.findByUserLabSessionIdAndQuestionId(labSessionId, questionId);
        return latestSubmission.map(Submission::getStatus).orElse(SubmissionStatus.NOT_SUBMISSION);
    }
}
