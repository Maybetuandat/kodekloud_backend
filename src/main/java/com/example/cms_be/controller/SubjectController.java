package com.example.cms_be.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cms_be.model.Subject;
import com.example.cms_be.service.SubjectService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PutMapping;


@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
@Slf4j
public class SubjectController {
        private final SubjectService SubjectService;



        @GetMapping("")
       public ResponseEntity<?> getAllSubjects() {
           try {
               List<Subject> subjects = SubjectService.getAllSubjects();
               return ResponseEntity.ok(subjects);
           } catch (Exception e) {
               log.error("Error fetching all subjects: {}", e.getMessage());
               return ResponseEntity.status(500).body("Failed to fetch subjects");
           }
       }
        @GetMapping("/{id}")
        public ResponseEntity<?> getSubjectById(@PathVariable Integer id) {
            try {
                 Subject Subject = SubjectService.getSubjectById(id);
                 return ResponseEntity.ok(Subject);
            } catch (Exception e) {
                 log.error("Error fetching Subject by ID {}: {}", id, e.getMessage());
                 return ResponseEntity.status(404).body("Subject not found");
            }
         }
       @PostMapping("")
       public ResponseEntity<?> createSubject(@RequestBody Subject Subject) {
           try {
               Subject createdSubject = SubjectService.createSubject(Subject);
               return ResponseEntity.ok(createdSubject);
           } catch (Exception e) {
               log.error("Error creating Subject: {}", e.getMessage());
               return ResponseEntity.status(500).body("Failed to create Subject");
           }
       }
       @PutMapping("/{id}")
       public ResponseEntity<?> updateSubject(@PathVariable Integer id, @RequestBody Subject Subject) {
           try {
               Subject updatedSubject = SubjectService.updateSubject(id, Subject);
               return ResponseEntity.ok(updatedSubject);
           } catch (Exception e) {
               log.error("Error updating Subject with ID {}: {}", id, e.getMessage());
               return ResponseEntity.status(404).body("Subject not found");
           }
       }
       @DeleteMapping("/{id}")
       public ResponseEntity<?> deleteSubject(@PathVariable Integer id) {
           try {
               Boolean deleted = SubjectService.deleteSubject(id);
               if (deleted) {
                   return ResponseEntity.ok("Subject deleted successfully");
               } else {
                   return ResponseEntity.status(404).body("Subject not found");
               }
           } catch (Exception e) {
               log.error("Error deleting Subject with ID {}: {}", id, e.getMessage());
               return ResponseEntity.status(500).body("Failed to delete Subject");
           }
       }

}
