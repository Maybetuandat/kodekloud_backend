package com.example.cms_be.service;
import java.util.Optional;

import com.example.cms_be.dto.lab.CreateLabRequest;
import com.example.cms_be.model.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import com.example.cms_be.repository.InstanceTypeRepository;
import com.example.cms_be.repository.LabRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabService {

    private final LabRepository labRepository;
    private final InstanceTypeRepository instanceTypeRepository;
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
            return labRepository.findLabsByCourseId(courseId, keyword, isActive, pageable);
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


        private String generatedNameSpace(String labTitle) {
        return labTitle
                .trim()
                .replaceAll("\\s+", "-")
                .toLowerCase();
    }

    public Lab createLab(CreateLabRequest createLabRequest) {
        try{
            if(createLabRequest.getCategoryId() == null || createLabRequest.getInstanceTypeId() == null) {
                throw new RuntimeException("CategoryId and InstanceTypeId cannot be null");
            }
            Lab lab = new Lab();

            lab.setNamespace(generatedNameSpace(createLabRequest.getTitle()));
            InstanceType instanceType = instanceTypeRepository.findById(createLabRequest.getInstanceTypeId())
                    .orElseThrow(() -> new RuntimeException("InstanceType not found with id: " + createLabRequest.getInstanceTypeId()));
            lab.setInstanceType(instanceType);
            if(createLabRequest.getTitle() != null) {
                lab.setTitle(createLabRequest.getTitle());
            }
            if(createLabRequest.getDescription() != null) {
                lab.setDescription(createLabRequest.getDescription());
            }
            if(createLabRequest.getEstimatedTime() != null) {
                lab.setEstimatedTime(createLabRequest.getEstimatedTime());
            }
          

            return labRepository.save(lab);
        }
        catch (Exception e) {
            log.error("Error creating lab: {}", e.getMessage());
            throw new RuntimeException("Failed to create lab", e);
        }
    }
    public Optional<Lab> getLabById(Integer id) {
        try {
              return labRepository.findByIdWithCategory(id);
        } catch (Exception e) {
            log.error("Error fetching lab by ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to fetch lab by ID", e);
        }
    }
    public Lab updateLab(Integer id, CreateLabRequest labUpdate) {
        try {
            Optional<Lab> existingLabOpt = labRepository.findById(id);
            if (existingLabOpt.isEmpty()) {
                return null;
            }

            Lab existingLab = existingLabOpt.get();


            if(labUpdate.getInstanceTypeId() != null && labUpdate.getInstanceTypeId() != existingLab.getInstanceType().getId()) {
                Integer newInstanceTypeId = labUpdate.getInstanceTypeId();
                InstanceType newInstanceType = instanceTypeRepository.findById(newInstanceTypeId).orElse(null);
                if (newInstanceType != null) {
                    existingLab.setInstanceType(newInstanceType);
                } else {
                    log.warn("InstanceType with ID {} not found. Skipping instance type update.", newInstanceTypeId);
                }
            }
            if(labUpdate.getTitle() != null) {
                existingLab.setTitle(labUpdate.getTitle());
            }
            if(labUpdate.getDescription() != null)
            {
                existingLab.setDescription(labUpdate.getDescription());
            }

            if(labUpdate.getEstimatedTime() != null) {
                existingLab.setEstimatedTime(labUpdate.getEstimatedTime());
            }
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

}
