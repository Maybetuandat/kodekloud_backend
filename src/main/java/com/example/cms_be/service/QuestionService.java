package com.example.cms_be.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.cms_be.model.Lab;
import com.example.cms_be.model.Question;
import com.example.cms_be.repository.QuestionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor

public class QuestionService {
    private final QuestionRepository questionRepository;
    private final LabService labService;

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

            Optional<Lab> lab = labService.getLabById(labId);
            if (lab.isPresent()) {
                question.setLab(lab.get());
            } else {
                throw new RuntimeException("Lab not found with id: " + labId);
            }
            return questionRepository.save(question);
        } catch (Exception e) {
            log.error("Error creating question: {}", e.getMessage());
            throw new RuntimeException("Failed to create question", e);
        }
    }
    

    

    public Question updateQuestion(Integer id, Question updatedQuestion) {
        try {
            var existingQuestion = questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question not found with id: " + id));

            existingQuestion.setQuestion(updatedQuestion.getQuestion());
            existingQuestion.setHint(updatedQuestion.getHint());
            existingQuestion.setSolution(updatedQuestion.getSolution());
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
