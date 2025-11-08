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

    private final SubjectRepository subjectRepository;


    public Subject createSubject(Subject subject) {
        try {

            return subjectRepository.save(subject);
        } catch (Exception e) {
            log.error("Error creating Subject: {}", e.getMessage());
            throw new RuntimeException("Failed to create Subject", e);
        }
    }

    public List<Subject> getAllSubjects() {
        try {
            return subjectRepository.findAll();
        } catch (Exception e) {
            log.error("Error fetching all Subjects: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch all Subjects", e);
        }
    }

    public Subject getSubjectById(Integer id) {
        try {
            return subjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subject not found with id: " + id));
        } catch (Exception e) {
            log.error("Error fetching Subject by ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to fetch Subject by ID", e);
        }
    }

    public Subject updateSubject(Integer id, Subject updatedSubject) {

        try {
            var existingSubject = subjectRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Subject not found with id: " + id));

            existingSubject.setTitle(updatedSubject.getTitle());
            existingSubject.setDescription(updatedSubject.getDescription());
            existingSubject.setIsActive(updatedSubject.getIsActive());
            existingSubject.setCode(updatedSubject.getCode());
            existingSubject.setUpdatedAt(LocalDateTime.now());

            return subjectRepository.save(existingSubject);
        } catch (Exception e) {
            log.error("Error updating Subject: {}", e.getMessage());
            throw new RuntimeException("Failed to update Subject", e);
        }
    }


    public Boolean deleteSubject(Integer id) {
        try {
            var existingSubject = subjectRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Subject not found with id: " + id));
            subjectRepository.delete(existingSubject);
            return true;
        } catch (Exception e) {
            log.error("Error deleting Subject: {}", e.getMessage());
            throw new RuntimeException("Failed to delete Subject", e);
        }
    }


}
