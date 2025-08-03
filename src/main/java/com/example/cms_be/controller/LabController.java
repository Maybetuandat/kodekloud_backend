package com.example.cms_be.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cms_be.model.Lab;
import com.example.cms_be.service.KubernetesService;
import com.example.cms_be.service.LabService;
import com.example.cms_be.service.SetupExecutionService;
import com.example.cms_be.ultil.SocketConnectionInfo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;




@Slf4j
@RestController
@RequestMapping("/api/labs")
@RequiredArgsConstructor
public class LabController {
    private final LabService labService; 

    private final SetupExecutionService setupExecutionService;
    private final KubernetesService kubernetesService;

    

    private static final String WEBSOCKET_ENDPOINT = "/ws/pod-logs";


    @GetMapping
    public ResponseEntity<?> getAllLabs(
        @RequestParam(required = false) Boolean isActivate,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ) {
        try {

            log.info("Fetching labs with isActivate: {}, page: {}, size: {}, sortBy: {}, sortDir: {}, search: {}", 
                isActivate, page, size, sortBy, sortDir, search);





            Pageable pageable = PageRequest.of(page, size, 
                sortDir.equalsIgnoreCase("desc") ? 
                    Sort.by(sortBy).descending() : 
                    Sort.by(sortBy).ascending()
            );
            
            Page<Lab> labPage;
            if (search != null && !search.trim().isEmpty()) {
            
                    if (isActivate != null) {
                        // Search + filter by status
                        labPage = labService.searchLabsByActivateStatus(search.trim(), isActivate, pageable);
                    } else {
                        // Chỉ search
                        labPage = labService.searchLabs(search.trim(), pageable);
                    }
        } else {
            // Không có search, chỉ filter
            if (isActivate != null) {
                labPage = labService.getLabsByActivateStatus(isActivate, pageable);
            } else {
                labPage = labService.getAllLabs(pageable);
            }
        }
            
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

    /**
     * Lấy thông tin chi tiết của một lab theo ID
     * GET /api/lab/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Lab> getLabById(@PathVariable String id) {
        try {
            Optional<Lab> lab = labService.getLabById(id);
            if (lab.isPresent()) {
                return ResponseEntity.ok(lab.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting lab by id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

      /**
     * Tạo mới một lab
     * POST /api/lab
     */
    @PostMapping()
    public ResponseEntity<?> createLab(@Valid @RequestBody Lab lab, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            // Xử lý lỗi validation
            Map<String, String> errors = new HashMap<>();
            bindingResult.getFieldErrors().forEach(error -> 
                errors.put(error.getField(), error.getDefaultMessage())
            );
            return ResponseEntity.badRequest().body(errors);
        }
    
        Lab createdLab = labService.createLab(lab);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdLab);
    }
    

      /**
     * Cập nhật thông tin lab
     * PUT /api/lab/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateLab(@PathVariable String id, 
                                       @Valid @RequestBody Lab lab, 
                                       BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            bindingResult.getFieldErrors().forEach(error -> 
                errors.put(error.getField(), error.getDefaultMessage())
            );
            return ResponseEntity.badRequest().body(errors);
        }

        try {
            Lab updatedLab = labService.updateLab(id, lab);
            if (updatedLab != null) {
                return ResponseEntity.ok(updatedLab);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error updating lab {}: {}", id, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể cập nhật lab: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    /**
     * Xóa lab 
     * DELETE /api/lab/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLab(@PathVariable String id) {
        try {
            boolean deleted = labService.deleteLab(id);
            if (deleted) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Lab đã được xóa thành công");
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error deleting lab {}: {}", id, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể xóa lab: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

     /**
     * Kích hoạt/vô hiệu hóa lab
     * PUT /api/lab/{id}/toggle-status
     */
    @PutMapping("/{id}/toggle-status")
    public ResponseEntity<?> toggleLabStatus(@PathVariable String id) {
        try {
            Lab updatedLab = labService.toggleLabStatus(id);
            if (updatedLab != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Trạng thái lab đã được cập nhật");
                response.put("lab", updatedLab);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error toggling lab status {}: {}", id, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể thay đổi trạng thái lab: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }


    
     /**
     * Tạo và test lab với setup steps execution
     * Trả về thông tin kết nối WebSocket để theo dõi realtime logs
     */
    @PostMapping("/test/{labId}")
    public ResponseEntity<?> testSetupStepForLab(@PathVariable String labId) {
        try {
            log.info("Starting test execution for lab: {}", labId);

            
            Optional<Lab> labOpt = labService.getLabById(labId);
            if (labOpt.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Lab không tồn tại với ID: " + labId);
                return ResponseEntity.notFound().build();
            }

            Lab lab = labOpt.get();
            
            
            String podName = kubernetesService.createLabPod(lab);
            log.info("Successfully created test pod {} for lab {}", podName, labId);

            // Bắt đầu thực thi setup steps bất đồng bộ
            CompletableFuture<Boolean> executionFuture = setupExecutionService.executeSetupStepsForAdminTest(labId, podName);
            
            // Tạo WebSocket connection info
            Map<String, Object> websocketInfo = SocketConnectionInfo.createWebSocketConnectionInfo(podName);
            
            // Tạo response với đầy đủ thông tin
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Lab test đã được khởi tạo thành công");
            response.put("labId", labId);
            response.put("labName", lab.getName());
            response.put("podName", podName);
            response.put("namespace", "default");
            response.put("websocket", websocketInfo);
            response.put("executionStarted", true);
            response.put("createdAt", java.time.LocalDateTime.now().toString());
            
            // Log execution future để track (không đợi kết quả)
            executionFuture.whenComplete((success, throwable) -> {
                if (throwable != null) {
                    log.error("Setup execution failed for lab {} on pod {}: {}", labId, podName, throwable.getMessage());
                } else {
                    log.info("Setup execution completed for lab {} on pod {} with result: {}", labId, podName, success);
                }
            });
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing lab {}: {}", labId, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Không thể tạo test pod: " + e.getMessage());
            error.put("labId", labId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Lấy trạng thái của pod test
     */
    @GetMapping("/test/{labId}/status")
    public ResponseEntity<?> getTestStatus(@PathVariable String labId, @RequestParam String podName) {
        try {
            String podStatus = kubernetesService.getPodStatus(podName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("labId", labId);
            response.put("podName", podName);
            response.put("status", podStatus);
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting test status for lab {} pod {}: {}", labId, podName, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể lấy trạng thái test: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Dừng và xóa pod test
     */
    @DeleteMapping("/test/{labId}")
    public ResponseEntity<?> stopTestExecution(@PathVariable String labId, @RequestParam String podName) {
        try {
            kubernetesService.deleteLabPod(podName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Test pod đã được dừng và xóa thành công");
            response.put("labId", labId);
            response.put("podName", podName);
            response.put("stoppedAt", java.time.LocalDateTime.now().toString());
            
            log.info("Successfully stopped and deleted test pod {} for lab {}", podName, labId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error stopping test for lab {} pod {}: {}", labId, podName, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể dừng test pod: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Lấy thông tin kết nối WebSocket cho pod cụ thể
     */
    @GetMapping("/test/websocket-info")
    public ResponseEntity<?> getWebSocketInfo(@RequestParam String podName) {
        try {
            Map<String, Object> websocketInfo = SocketConnectionInfo.createWebSocketConnectionInfo(podName);
            return ResponseEntity.ok(websocketInfo);
            
        } catch (Exception e) {
            log.error("Error getting websocket info for pod {}: {}", podName, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể lấy thông tin WebSocket: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

  

    
    
}