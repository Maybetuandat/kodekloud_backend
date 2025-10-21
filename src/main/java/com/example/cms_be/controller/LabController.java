package com.example.cms_be.controller;


import org.springframework.web.bind.annotation.*;

import com.example.cms_be.model.Lab;
import com.example.cms_be.model.Question;
import com.example.cms_be.service.LabService;
import com.example.cms_be.service.QuestionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.HashMap;

import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;




@Slf4j
@RestController
@RequestMapping("/api/labs")
@RequiredArgsConstructor
public class LabController {
    private final LabService labService;
    private final QuestionService questionService;

    @GetMapping("/{labId}")
    public ResponseEntity<?> getLabById(@PathVariable Integer labId) {
        try {
            Optional<Lab> lab = labService.getLabById(labId);
            return lab.map(ResponseEntity::ok)
                      .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (Exception e) {
            log.error("Error getting lab: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    

    @GetMapping("/{labId}/questions")
   public ResponseEntity<?> getAllQuestionWithPagination(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String search,
            @PathVariable Integer labId
   ) {
      
    try {
        int pageNumber = page > 0 ? page - 1 : 0;
        if (search != null) {
            search = search.trim(); 
        }
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        
        Page<Question> questionPage = questionService.getAllQuestionsWithPaginations(search, labId, pageable);
        Map<String, Object> response = Map.of(
            "data", questionPage.getContent(),
            "currentPage", questionPage.getNumber() + 1,
            "totalItems", questionPage.getTotalElements(),
            "totalPages", questionPage.getTotalPages(), 
            "hasNext", questionPage.hasNext(),
            "hasPrevious", questionPage.hasPrevious()
        );
        return ResponseEntity.ok(response); 
    } catch (Exception e) {
          log.error("Error occurred while fetching questions with search: {}", search, e);
            return ResponseEntity.status(500).body("An error occurred while fetching questions.");
    }
   }



    @PostMapping("/{labId}/questions")
    public ResponseEntity<Question> createQuestionInLab(
            @PathVariable Integer labId,
            @RequestBody Question question
    ) {
       try {
         Question createdQuestion = questionService.createQuestion(labId, question);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdQuestion);
       } catch (Exception e) {
           log.error("Error creating question in lab: {}", e.getMessage());
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
       }
    }

    @PutMapping("/{labId}")
    public ResponseEntity<Lab> updateLab(
            @PathVariable Integer labId,
            @Valid @RequestBody Lab lab
    ) {
       try {
         Lab updatedLab = labService.updateLab(labId, lab);
        return ResponseEntity.ok(updatedLab);
       } catch (Exception e) {
           log.error("Error updating lab: {}", e.getMessage());
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
       }
    }

    @DeleteMapping("/{labId}")
    public ResponseEntity<?> deleteLab(@PathVariable Integer labId) {
       try {
         boolean isDeleted = labService.deleteLab(labId);
        return ResponseEntity.ok(Map.of("message", "Lab with id " + labId + " has been deleted successfully."));
       } catch (Exception e) {
           log.error("Error deleting lab: {}", e.getMessage());
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
       }
    }

   
}