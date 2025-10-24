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

import com.example.cms_be.dto.RequestSwapSetupStep;
import com.example.cms_be.model.SetupStep;

import com.example.cms_be.service.SetupStepService;



import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping("/api/setup-steps")
@RequiredArgsConstructor
public class SetupStepController {
    private final SetupStepService setupStepService;

    @PutMapping("/{setupStepId}")
    public ResponseEntity<?> updateSetupStep(@PathVariable Integer setupStepId, @RequestBody SetupStep setupStep) {

        try {
            SetupStep updatedSetupStep = setupStepService.updateSetupStep( setupStep, setupStepId);
            if (updatedSetupStep != null) {
                return ResponseEntity.ok(updatedSetupStep);
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Setup step không tồn tại với ID: " + setupStepId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error updating setup step: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể cập nhật setup step: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

   
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


    @PostMapping("/swap")
    public ResponseEntity<?>   swapOrderSetupStep(@RequestBody RequestSwapSetupStep request) {

        try {
            setupStepService.swapOrderSetupStep(request.fromSetupStepId(), request.toSetupStepId());
            Map<String, String> response = new HashMap<>();
            response.put("message", "Swap setup step successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error swapping setup steps {} and {}: {}", request.fromSetupStepId(), request.toSetupStepId(), e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Cant swap setup step: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
        
        
    }
    


  

}
