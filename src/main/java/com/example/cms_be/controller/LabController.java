package com.example.cms_be.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cms_be.model.Lab;
import com.example.cms_be.service.LabService;

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
@RequestMapping("/api/lab")
@RequiredArgsConstructor
public class LabController {
     private final LabService labService; 

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
     * Lấy danh sách setup steps của lab
     * GET /api/lab/{id}/setup-steps
     */
    @GetMapping("/{id}/setup-steps")
    public ResponseEntity<?> getLabSetupSteps(@PathVariable String id) {
        try {
            var setupSteps = labService.getLabSetupSteps(id);
            return ResponseEntity.ok(setupSteps);
        } catch (Exception e) {
            log.error("Error getting setup steps for lab {}: {}", id, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể lấy danh sách setup steps: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}