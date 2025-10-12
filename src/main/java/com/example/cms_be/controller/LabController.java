package com.example.cms_be.controller;

import com.example.cms_be.model.CourseUser;
import com.example.cms_be.model.User;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.service.VMService;
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
    private final SocketConnectionInfo socketConnectionInfo;
    private final VMService vmService;

    private static final String WEBSOCKET_ENDPOINT = "/ws/pod-logs";

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

    @PostMapping("create-lab-session")
    public ResponseEntity<?> createLabSession(@Valid @RequestBody Lab lab, CourseUser courseUser, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            // Xử lý lỗi validation
            Map<String, String> errors = new HashMap<>();
            bindingResult.getFieldErrors().forEach(error ->
                    errors.put(error.getField(), error.getDefaultMessage())
            );
            return ResponseEntity.badRequest().body(errors);
        }
        UserLabSession newUserLabSession = new UserLabSession();
        return ResponseEntity.status(HttpStatus.CREATED).body(newUserLabSession);
    }

    @GetMapping("test")
    public int testLab() {
        return 1;
    }
}