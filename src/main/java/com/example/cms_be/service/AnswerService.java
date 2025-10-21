package com.example.cms_be.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.cms_be.model.Answer;
import com.example.cms_be.repository.AnswerRepository;
import com.example.cms_be.repository.QuestionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;

   
    public List<Answer> getAllAnswers(Integer questionId)
    {
        try {
            return answerRepository.findByQuestionId(questionId);
        } catch (Exception e) {
            log.error("Error fetching answers for question id {}: {}", questionId, e.getMessage());
            throw new RuntimeException("Failed to fetch answers", e);
        }
    }

    public Answer getAnswerById(Integer id) {
        try {
            return answerRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Answer not found with id: " + id));
        } catch (Exception e) {
            log.error("Error fetching answer by id: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch answer", e);
        }
    }
    public Answer createAnswer(Answer answer, Integer questionId) {
        try {
            var question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found with id: " + questionId));
            answer.setQuestion(question);
            return answerRepository.save(answer);
        } catch (Exception e) {
            log.error("Error creating answer: {}", e.getMessage());
            throw new RuntimeException("Failed to create answer", e);
        }
    }
    public Answer updateAnswer(Integer id, Answer updatedAnswer) {
      try {
        var existingAnswer = answerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Answer not found with id: " + id));

        existingAnswer.setContent(updatedAnswer.getContent());
        existingAnswer.setIsRightAns(updatedAnswer.getIsRightAns());

        return answerRepository.save(existingAnswer);
      } catch (Exception e) {
        log.error("Error updating answer: {}", e.getMessage());
        throw new RuntimeException("Failed to update answer", e);
      }
    }
    public Boolean deleteAnswer(Integer id) {
        try {
             var existingAnswer = answerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Answer not found with id: " + id));
        answerRepository.delete(existingAnswer);
        return true;
        } catch (Exception e) {
            log.error("Error deleting answer: {}", e.getMessage());
            throw new RuntimeException("Failed to delete answer", e);
        }
    }
    
}
