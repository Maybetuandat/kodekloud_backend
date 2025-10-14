package com.example.cms_be.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cms_be.model.SetupStep;
import com.example.cms_be.service.LabService;
import com.example.cms_be.service.SetupStepService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping("/api/labs/setup-steps")
@RequiredArgsConstructor
public class SetupStepController {
    private final SetupStepService setupStepService;
    private final LabService labService;


    /**
     * Lấy danh sách setup steps của lab
     * GET /api/lab/{id}/setup-steps
     */
    @GetMapping("/{labId}")
    public ResponseEntity<?> getLabSetupSteps(@PathVariable Integer labId) {
        try {
            var setupSteps = labService.getLabSetupSteps(labId);
            return ResponseEntity.ok(setupSteps);
        } catch (Exception e) {
            log.error("Error getting setup steps for lab {}: {}", labId, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể lấy danh sách setup steps: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

     /**
     * Tạo mới setup step
     * POST /api/setup-step/{labId} - Tạo mới setup step cho lab với  labID cụ thể
     */
    @PostMapping("/{labId}")
    public ResponseEntity<?> createSetupStep(
        @PathVariable Integer labId,
        @Valid @RequestBody SetupStep setupStep,
        BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            bindingResult.getFieldErrors().forEach(error -> 
                errors.put(error.getField(), error.getDefaultMessage())
            );
            return ResponseEntity.badRequest().body(errors);
        }

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

    /**
     * Tạo nhiều setup steps cùng lúc cho một lab
     * POST /api/setup-step/batch/{labId}
     */

    @PostMapping("/batch/{labId}")
    public ResponseEntity<?> createBatchSetupSteps(@PathVariable Integer labId,
                                                   @Valid @RequestBody List<SetupStep> setupSteps,
                                                   BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            bindingResult.getFieldErrors().forEach(error -> 
                errors.put(error.getField(), error.getDefaultMessage())
            );
            return ResponseEntity.badRequest().body(errors);
        }

        try {
            List<SetupStep> createdSetupSteps = setupStepService.createBatchSetupSteps(labId, setupSteps);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Đã tạo thành công " + createdSetupSteps.size() + " setup steps cho lab " + labId);
            response.put("setupSteps", createdSetupSteps);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating batch setup steps for lab {}: {}", labId, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể tạo batch setup steps: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }



    /**
     * Cập nhật setup step
     * PUT /api/setup-step/ - Cập nhật setup step với ID cụ thể
     */
    @PutMapping()
    public ResponseEntity<?> updateSetupStep(@RequestBody SetupStep setupStep,
                                             BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            bindingResult.getFieldErrors().forEach(error -> 
                errors.put(error.getField(), error.getDefaultMessage())
            );
            return ResponseEntity.badRequest().body(errors);
        }

        try {
            SetupStep updatedSetupStep = setupStepService.updateSetupStep( setupStep);
            if (updatedSetupStep != null) {
                return ResponseEntity.ok(updatedSetupStep);
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Setup step không tồn tại với ID: "     );
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error updating setup step: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể cập nhật setup step: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Xóa một setup step
     * DELETE /api/setup-step/{id}
    */
    @DeleteMapping("/{setupStepId}")
    public ResponseEntity<?> deleteSetupStep(@PathVariable Integer setupStepId) {
        try {
            boolean deleted = setupStepService.deleteSetupStep(setupStepId);
            if (deleted) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Setup step đã được xóa thành công");
                return ResponseEntity.ok(response);
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Setup step không tồn tại với ID: " + setupStepId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error deleting setup step {}: {}", setupStepId, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể xóa setup step: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }


     /**
     * Xóa nhiều setup steps cùng lúc
     * DELETE /api/setup-step/batch
     */
    @DeleteMapping("/batch")
    public ResponseEntity<?> deleteBatchSetupSteps(@RequestBody List<Integer> setupStepIds) {
        try {
            if (setupStepIds == null || setupStepIds.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Danh sách ID setup steps không được để trống");
                return ResponseEntity.badRequest().body(error);
            }

            int deletedCount = setupStepService.deleteBatchSetupSteps(setupStepIds);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Đã xóa thành công " + deletedCount + " setup steps");
            response.put("deletedCount", deletedCount);
            response.put("requestedCount", setupStepIds.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting batch setup steps: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể xóa batch setup steps: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

}
