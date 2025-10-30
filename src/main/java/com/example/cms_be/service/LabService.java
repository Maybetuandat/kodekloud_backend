package com.example.cms_be.service;
import java.util.Optional;

import com.example.cms_be.model.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;


import com.example.cms_be.repository.LabRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabService {

    private final LabRepository labRepository;
   public Page<Lab> getAllLabs(Pageable pageable, Boolean isActive, String keyword) {
       try {
        return labRepository.findWithFilters(keyword, isActive, pageable );
       } catch (Exception e) {
           log.error("Error fetching labs: {}", e.getMessage());
           return Page.empty();
       }
   }


    public Page<Lab> getLabsByCourseId(Integer courseId, String keyword, Boolean isActive, Pageable pageable) {
        try {


            Page<Lab> labsInCourse = labRepository.findLabsByCourseId(courseId, keyword, isActive, pageable);
            
            System.out.println(labsInCourse.getContent().size());
            System.out.println("Total elements: " + labsInCourse.getTotalElements());
            System.out.println("Total pages: " + labsInCourse.getTotalPages());

            System.out.println("Has next: " + labsInCourse.hasNext());
            System.out.println("Has previous: " + labsInCourse.hasPrevious());
            System.out.println("Current page number: " + labsInCourse.getNumber());
            System.out.println("Page size: " + labsInCourse.getSize());
            return labsInCourse;

        } catch (Exception e) {
            log.error("Error fetching labs by course ID {}: {}", courseId, e.getMessage());
            return Page.empty();
        }
    }

    public Page<Lab> getLabsNotInCourse(Integer courseId, String keyword, Boolean isActive, Pageable pageable) {
        try {
            return labRepository.findLabsNotInCourseId(courseId, keyword, isActive, pageable);
        } catch (Exception e) {
            log.error("Error fetching labs not in course ID {}: {}", courseId, e.getMessage());
            return Page.empty();
        }
    }



    public Lab createLab(Lab lab) {
        Lab createLab = new Lab();

        try{

            createLab = labRepository.save(lab);
            log.info("Lab created successfully with ID: {}", createLab.getId());

        }
        catch (Exception e) {
            log.error("Error creating lab: {}", e.getMessage());
            throw new RuntimeException("Failed to create lab", e);
        }
        return createLab;
    }
    public Optional<Lab> getLabById(Integer id) {
        try {
            return labRepository.findById(id);
        } catch (Exception e) {
            log.error("Error fetching lab by ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to fetch lab by ID", e);
        }
    }
    public Lab updateLab(Integer id, Lab labUpdate) {
        try {
            Optional<Lab> existingLabOpt = labRepository.findById(id);
            if (existingLabOpt.isEmpty()) {
                return null;
            }

            Lab existingLab = existingLabOpt.get();

            // Update fields
            existingLab.setTitle(labUpdate.getTitle());
            existingLab.setDescription(labUpdate.getDescription());
            
            existingLab.setEstimatedTime(labUpdate.getEstimatedTime());

            if (labUpdate.getIsActive() != null) {
                existingLab.setIsActive(labUpdate.getIsActive());
            }

            Lab updatedLab = labRepository.save(existingLab);
            log.info("Lab updated successfully with ID: {}", updatedLab.getId());
            return updatedLab;
        } catch (Exception e) {
            log.error("Error updating lab {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to update lab", e);
        }
    }


    public Lab toggleLabActivation(Integer id)
    {
        try {
            
            Lab existingLabOpt = labRepository.findById(id).orElse(null);
            if (existingLabOpt == null) {
                log.error("Lab not found for ID: {}", id);
                throw new RuntimeException("Lab not found");
            }
            existingLabOpt.setIsActive(!existingLabOpt.getIsActive());
            Lab updatedLab = labRepository.save(existingLabOpt);
            log.info("Lab activation toggled successfully for ID: {}, new status: {}", id, updatedLab.getIsActive());
            return updatedLab;

        } catch (Exception e) {
            log.error("Error toggling lab activation {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to toggle lab activation", e);
        }
    }
    public boolean deleteLab(Integer id) {
        try {
            labRepository.deleteById(id);
            return true;
        }
        catch (Exception e) {
            log.error("Error deleting lab {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to delete lab", e);
        }
    }
  
   

    public UserLabSession createUserLabSession(Lab lab, CourseUser courseUser) {
        UserLabSession newLabSession = new UserLabSession();

        try{

        }
        catch (Exception e) {
            throw new RuntimeException("Failed to create lab", e);
        }
        return newLabSession;
    }

}
