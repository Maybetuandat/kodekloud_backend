package com.example.cms_be.controller;
import com.example.cms_be.dto.BackingImageDTO;
import com.example.cms_be.dto.lab.CreateLabRequest;
import com.example.cms_be.dto.lab.LabTestRequest;
import com.example.cms_be.dto.lab.LabTestResponse;

import org.springframework.web.bind.annotation.*;
import com.example.cms_be.model.Lab;
import com.example.cms_be.model.Question;
import com.example.cms_be.model.SetupStep;
import com.example.cms_be.service.LabService;
import com.example.cms_be.service.QuestionService;
import com.example.cms_be.service.SetupStepService;
import com.example.cms_be.service.StorageService;
import com.example.cms_be.service.VMTestService;

import io.kubernetes.client.openapi.ApiException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
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
    private final StorageService    storageService;
     private final VMTestService vmTestService;

    
    @GetMapping("")
    public ResponseEntity<?> getLabWithPagination(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Integer categorySlug
    ) {
        try {
            int pageNumber = page > 0 ? page - 1 : 0;
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            Page<Lab> labPage = labService.getAllLabs(pageable, isActive, search);

            Map<String, Object> response = new HashMap<>();
            response.put("data", labPage.getContent());
            response.put("currentPage", labPage.getNumber() + 1);
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

   



    @GetMapping("/backing-images")
    public ResponseEntity<?> getAllBackingImages() {
        try {
            List<BackingImageDTO> backingImages = storageService.getAllBackingImages();
            return ResponseEntity.ok(backingImages);
        } catch (ApiException e) {
            log.error("Failed to fetch Longhorn backing images due to Kubernetes API error.");
            return ResponseEntity
                    .status(e.getCode()) 
                    .body(Map.of("error", "Failed to communicate with Kubernetes API", "details", e.getResponseBody()));
        } catch (Exception e) {
            log.error("An unexpected error occurred while fetching Longhornco backing images.", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An internal server error occurred."));
        }
    }



    @GetMapping("/test/{testId}/status")
    public ResponseEntity<?> getTestStatus(@PathVariable String testId) {
        try {
            LabTestResponse response = vmTestService.getTestStatus(testId);
            return ResponseEntity.ok(response);

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Test not found: " + testId));

        } catch (Exception e) {
            log.error("Error getting test status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get test status"));
        }
    }



    @PostMapping("")
    public ResponseEntity<Lab> createLab(@RequestBody CreateLabRequest lab) {
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

    
    
    
    
    
    
   @PostMapping("/test")
    public ResponseEntity<?> testLabWithConfig(@Valid @RequestBody LabTestRequest request) {
        try {
            log.info("Received test request with config: {}", request);

            LabTestResponse response = vmTestService.startLabTest(request);

            return ResponseEntity.accepted().body(response);

        } catch (EntityNotFoundException e) {
            log.error("Lab not found: {}", request.getLabId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error starting lab test: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to start lab test: " + e.getMessage()));
        }
    }
    
    
    
    
    @PatchMapping("/{labId}")
    public ResponseEntity<Lab> updateLab(
            @PathVariable Integer labId,
            @Valid @RequestBody CreateLabRequest lab
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

   

    @DeleteMapping("/test/{testId}")
    public ResponseEntity<?> cancelTest(@PathVariable String testId) {
        try {
            vmTestService.cancelTest(testId);
            return ResponseEntity.ok(Map.of(
                    "message", "Test cancelled successfully",
                    "testId", testId
            ));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Test not found: " + testId));

        } catch (Exception e) {
            log.error("Error cancelling test: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to cancel test"));
        }
    }
   
}