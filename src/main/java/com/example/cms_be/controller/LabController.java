package com.example.cms_be.controller;


import org.springframework.web.bind.annotation.*;

import com.example.cms_be.model.Lab;

import com.example.cms_be.service.LabService;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


import java.util.HashMap;

import java.util.Map;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;



@Slf4j
@RestController
@RequestMapping("/api/labs")
@RequiredArgsConstructor
public class LabController {
    private final LabService labService;

    @GetMapping()
    public ResponseEntity<?> getAllLabs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size);
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

    @PostMapping()
    public ResponseEntity<?> createLab(@Valid @RequestBody Lab lab) {
        try {
            Lab createdLab = labService.createLab(lab);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdLab);
        } catch (Exception e) {
            log.error("Error creating lab: {}", e.getMessage());
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