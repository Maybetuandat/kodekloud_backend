package com.example.cms_be.service;



import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.cms_be.model.Subject;
import com.example.cms_be.repository.SubjectRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository SubjectRepository;


    public Subject createSubject(Subject Subject) {

        log.info("Creating Subject: {}", Subject);
          Subject createSubject = new Subject();
        try {
          
            createSubject = SubjectRepository.save(Subject);
            log.info("Subject created successfully with ID: {}", createSubject.getId());
        } catch (Exception e) {
            log.error("Error creating Subject: {}", e.getMessage());
            throw new RuntimeException("Failed to create Subject", e);
        }
        return createSubject;
    }

    public List<Subject> getAllSubjects() {
        return SubjectRepository.findAll();
    }

    public Subject getSubjectById(Integer id) {
        return SubjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subject not found with id: " + id));
    }

    public Subject updateSubject(Integer id, Subject updatedSubject) {

        try {
            var existingSubject = SubjectRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Subject not found with id: " + id));

            existingSubject.setTitle(updatedSubject.getTitle());
            existingSubject.setDescription(updatedSubject.getDescription());
            existingSubject.setIsActive(updatedSubject.getIsActive());
            existingSubject.setCode(updatedSubject.getCode());
            existingSubject.setUpdatedAt(LocalDateTime.now());

            return SubjectRepository.save(existingSubject);
        } catch (Exception e) {
            log.error("Error updating Subject: {}", e.getMessage());
            throw new RuntimeException("Failed to update Subject", e);
        }
    }


    public Boolean deleteSubject(Integer id) {
        try {
            var existingSubject = SubjectRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Subject not found with id: " + id));
            SubjectRepository.delete(existingSubject);
            return true;
        } catch (Exception e) {
            log.error("Error deleting Subject: {}", e.getMessage());
            throw new RuntimeException("Failed to delete Subject", e);
        }
    }


}
