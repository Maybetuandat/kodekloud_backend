package com.example.cms_be.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cms_be.model.CourseUser;
import com.example.cms_be.service.EnrollmentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/enrollments")
public class EnrollmentController {
    private final EnrollmentService enrollmentService;

    @PostMapping()
    public ResponseEntity<?> createEnrollment(@RequestParam("courseId") Integer courseId, @RequestParam("userId") Integer userId) {
        
        
        try {
            CourseUser courseUser = enrollmentService.createEnrollment(courseId, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(courseUser);
        } catch (Exception e) {
            log.error("Error creating enrollment: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    
}
