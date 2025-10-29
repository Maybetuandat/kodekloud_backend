package com.example.cms_be.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.cms_be.model.Answer;
import com.example.cms_be.model.Lab;
import com.example.cms_be.model.Question;
import com.example.cms_be.repository.AnswerRepository;
import com.example.cms_be.repository.LabRepository;
import com.example.cms_be.repository.QuestionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuestionService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final LabRepository labRepository;
    

    public Page<Question> getAllQuestionsWithPaginations(  String keyword, Integer labId, Pageable pageable)
    {
         try {
            return questionRepository.findWithFilters( keyword, labId, pageable);
         } catch (Exception e) {
            log.error("Error fetching questions with filters: {}", e.getMessage());
            return Page.empty();
         }
    }

    public Question getQuestionById(Integer id) {
        try {
            return questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question not found with id: " + id));
        } catch (Exception e) {
            log.error("Error fetching question by id: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch question", e);
        }
        
    }
   public Question createQuestion(Integer labId, Question question) {
    try {
        Lab lab = labRepository.findById(labId)
                .orElseThrow(() -> new RuntimeException("Lab not found with id: " + labId));

        question.setLab(lab);
        return questionRepository.save(question);

    } catch (Exception e) {
        log.error("Error creating question: {}", e.getMessage());
        throw new RuntimeException("Failed to create question", e);
    }
}

    

    public List<Question> createBulkQuestion(Integer labId, List<Question> questions) {
        Optional<Lab> labOptional = labRepository.findById(labId);
        if (labOptional.isEmpty()) {
            throw new RuntimeException("Lab not found with id: " + labId);
        }
        Lab lab = labOptional.get();
        for (Question question : questions) {
            question.setLab(lab); 
            if (question.getAnswers() != null) {
                for (Answer answer : question.getAnswers()) {
                    answer.setQuestion(question); 
                }
            }
           for(Answer a : question.getAnswers())
           {
            log.info("Setting question for answer: {} {}", a.getIsRightAns(), a.getContent());
           }
        }
        List<Question> savedQuestions = questionRepository.saveAll(questions);
        return savedQuestions;
    }

    public Question updateQuestion(Integer id, Question updatedQuestion) {
        try {
            Question existingQuestion = questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question not found with id: " + id));
            if (updatedQuestion.getQuestion() != null) {
                existingQuestion.setQuestion(updatedQuestion.getQuestion());
            }
            if (updatedQuestion.getHint() != null) {
                existingQuestion.setHint(updatedQuestion.getHint());
            }
            if (updatedQuestion.getSolution() != null) {
                existingQuestion.setSolution(updatedQuestion.getSolution());
            }
            
            if (updatedQuestion.getAnswers() != null) {
                if (existingQuestion.getAnswers() != null) {
                    existingQuestion.getAnswers().clear();
                }
                for (Answer answer : updatedQuestion.getAnswers()) {
                    answer.setQuestion(existingQuestion); 
                    existingQuestion.getAnswers().add(answer);
                }
            }
            existingQuestion.setUpdatedAt(LocalDateTime.now());
            return questionRepository.save(existingQuestion);
        } catch (Exception e) {
            log.error("Error updating question: {}", e.getMessage());
            throw new RuntimeException("Failed to update question", e);
        }
    }

    public Boolean deleteQuestion(Integer id) {
        try {
             var existingQuestion = questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question not found with id: " + id));
             questionRepository.delete(existingQuestion);
             return true;
        } catch (Exception e) {
            log.error("Error deleting question: {}", e.getMessage());
            throw new RuntimeException("Failed to delete question", e);
        }
       
    }
}
