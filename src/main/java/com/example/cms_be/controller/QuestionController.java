package com.example.cms_be.controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.example.cms_be.service.QuestionService;
import com.example.cms_be.model.Question;
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    @PostMapping("/create")
    public ResponseEntity<Question> createQuestion(@Valid @RequestBody Question question) {
        Question savedQuestion = questionService.createQuestion(question);
        return ResponseEntity.ok(savedQuestion);
    }

    @GetMapping("")
    public ResponseEntity<Question> getRandomQuestion() {
        Question question = questionService.getRandomQuestion();
        return ResponseEntity.ok(question);
    }
}
