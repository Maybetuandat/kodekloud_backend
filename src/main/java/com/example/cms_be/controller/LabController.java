package com.example.cms_be.controller;


import com.example.cms_be.dto.BackingImageDTO;
import com.example.cms_be.service.StorageService;
import io.kubernetes.client.openapi.ApiException;
import org.springframework.web.bind.annotation.*;

import com.example.cms_be.model.Answer;
import com.example.cms_be.model.Lab;
import com.example.cms_be.model.Question;
import com.example.cms_be.model.SetupStep;
import com.example.cms_be.service.AnswerService;
import com.example.cms_be.service.LabService;
import com.example.cms_be.service.QuestionService;
import com.example.cms_be.service.SetupStepService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.experimental.var;
import lombok.extern.slf4j.Slf4j;


import java.util.HashMap;

import java.util.List;
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
    private final SetupStepService setupStepService;
    private final StorageService storageService;

    
    @GetMapping("")
    public ResponseEntity<?> getLabWithPagination(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive
    ) {
        try {
            Pageable pageable = PageRequest.of(page, pageSize);
            Page<Lab> labPage = labService.getAllLabs(pageable, isActive, search);

            Map<String, Object> response = new HashMap<>();
            response.put("data", labPage.getContent());
            response.put("currentPage", labPage.getNumber());
            response.put("totalItems", labPage.getTotalElements());
            response.put("totalPages", labPage.getTotalPages());
            response.put("hasNext", labPage.hasNext());
            response.put("hasPrevious", labPage.hasPrevious());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting labs: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


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





    @GetMapping("/{labId}/setup-steps")
    public ResponseEntity<?> getLabSetupSteps(@PathVariable Integer labId) {
        try {
            var setupSteps = setupStepService.getLabSetupSteps(labId);
            return ResponseEntity.ok(setupSteps);
        } catch (Exception e) {
            log.error("Error getting setup steps for lab {}: {}", labId, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể lấy danh sách setup steps: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

   

    @PostMapping("")
    public ResponseEntity<?> createLab(@RequestBody Lab lab) {
        try {
            Lab createdLab = labService.createLab(lab);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdLab);
        } catch (Exception e) {
            log.error("Error creating lab: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    



    @PostMapping("/{labId}/setup-steps")
    public ResponseEntity<?> createSetupStep(
        @PathVariable Integer labId,
         @RequestBody SetupStep setupStep) {

       
        try {
            SetupStep createdSetupStep = setupStepService.createSetupStep(setupStep,  labId);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdSetupStep);
        } catch (Exception e) {
            log.error("Error creating setup step: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể tạo setup step: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/{labId}/questions")
    public ResponseEntity<Question> createQuestionInLab(
            @PathVariable Integer labId,
            @RequestBody Question question
    ) {
       try {

       System.out.println("Is calling create question");


        log.info("Creating question in lab with id {}: {}", labId, question);
         Question createdQuestion = questionService.createQuestion(labId, question);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdQuestion);
       } catch (Exception e) {
           log.error("Error creating question in lab: {}", e.getMessage());
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
       }
    }
    @PostMapping("{labId}/questions/bulk")
    public ResponseEntity<?> createBulkQuestion(
            @PathVariable Integer labId,
            @RequestBody List<Question> questions
    ) {
       try {
            
            List<Question> createdQuestions = questionService.createBulkQuestion(labId, questions);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdQuestions);
       } catch (Exception e) {
           log.error("Error creating bulk questions in lab: {}", e.getMessage());
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

    @PatchMapping("/{labId}/toggle-activation")
    public ResponseEntity<Lab> toggleLabActivation(@PathVariable Integer labId) {
         try {
            Lab updatedLab = labService.toggleLabActivation(labId);
            return ResponseEntity.ok(updatedLab);
        } catch (Exception e) {
            log.error("Error toggling lab activation: {}", e.getMessage());
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

    @GetMapping("/backing-images")
    public ResponseEntity<?> getAllBackingImages() {
        try {
            List<BackingImageDTO> backingImages = storageService.getAllBackingImages();
            return ResponseEntity.ok(backingImages);
        } catch (ApiException e) {
            log.error("Failed to fetch Longhorn backing images due to Kubernetes API error.");
            return ResponseEntity
                    .status(e.getCode()) // Trả về mã lỗi thực tế từ K8s
                    .body(Map.of("error", "Failed to communicate with Kubernetes API", "details", e.getResponseBody()));
        } catch (Exception e) {
            log.error("An unexpected error occurred while fetching Longhorn backing images.", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An internal server error occurred."));
        }
    }
   
}