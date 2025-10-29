package com.example.cms_be.controller;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.cms_be.service.AnswerService;
import com.example.cms_be.service.QuestionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.example.cms_be.model.Answer;
import com.example.cms_be.model.Question;



@RestController
@RequestMapping("/api/questions")
@Slf4j
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final AnswerService answerService;

  

    @GetMapping("/{questionId}/answers")
    public List<Answer> getAnswersByQuestionId(@PathVariable Integer questionId) {
        return answerService.getAllAnswers(questionId);
    }
   
    @PostMapping("/{questionId}/answers")
    public ResponseEntity<?> createAnswer(@PathVariable Integer questionId, @RequestBody Answer answer) {
        try {
            Answer createdAnswer = answerService.createAnswer(answer, questionId);
            return ResponseEntity.ok(createdAnswer);
        } catch (Exception e) {
            log.error("Error creating answer for question id {}: {}", questionId, e.getMessage());
            return ResponseEntity.status(500).body("An error occurred while creating the answer.");
        }
    }
  
    @PatchMapping("/{id}")
    public ResponseEntity<Question> updateQuestion(@PathVariable Integer id, @RequestBody Question questionDetails) {
        try {
            Question updatedQuestion = questionService.updateQuestion(id, questionDetails);
            return ResponseEntity.ok(updatedQuestion);
        } catch (Exception e) {
            log.error("Error updating question with id {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteQuestion(@PathVariable Integer id) {
        try {
            Boolean deleted = questionService.deleteQuestion(id);
            return ResponseEntity.ok(Map.of("deleted", deleted));
        } catch (Exception e) {
            log.error("Error deleting question with id {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).body("An error occurred while deleting the question.");
        }
    }
}
