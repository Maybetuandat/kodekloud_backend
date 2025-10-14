package com.example.cms_be.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.cms_be.model.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import com.example.cms_be.repository.LabRepository;

import com.example.cms_be.repository.SetupStepRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabService {

    private final LabRepository labRepository;
    private final SetupStepRepository setupStepRepository;
    
    

   public Page<Lab> getAllLabs(Pageable pageable, Boolean isActive, String keyword) {
       return labRepository.findWithFilters(keyword, isActive, pageable);
   }

    public Lab createLab(Lab lab) {
        Lab createLab = new Lab();

        try{
            createLab = labRepository.save(lab);
        }
        catch (Exception e) {
            log.error("Error creating lab: {}", e.getMessage());
            throw new RuntimeException("Failed to create lab", e);
        }
        return createLab;
    }
      /**
     * Lấy thông tin lab theo ID
     */
    public Optional<Lab> getLabById(Integer id) {
        try {
            return labRepository.findById(id);
        } catch (Exception e) {
            log.error("Error fetching lab by ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to fetch lab by ID", e);
        }
    }
     /**
     * Cập nhật thông tin lab
     */
    public Lab updateLab(Integer id, Lab labUpdate) {
        try {
            Optional<Lab> existingLabOpt = labRepository.findById(id);
            if (existingLabOpt.isEmpty()) {
                return null;
            }

            Lab existingLab = existingLabOpt.get();

            // Update fields
            existingLab.setName(labUpdate.getName());
            existingLab.setDescription(labUpdate.getDescription());
            existingLab.setBaseImage(labUpdate.getBaseImage());
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


    /**
     * Xóa lab
     */
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

    /**
     * Kích hoạt/vô hiệu hóa lab
     */
    public Lab toggleLabStatus(Integer id) {
        try {
            Optional<Lab> labOpt = labRepository.findById(id);
            if (labOpt.isEmpty()) {
                return null;
            }

            Lab lab = labOpt.get();
            lab.setIsActive(!lab.getIsActive());
            Lab updatedLab = labRepository.save(lab);

            log.info("Lab status toggled for ID: {}, new status: {}", id, updatedLab.getIsActive());
            return updatedLab;
        } catch (Exception e) {
            log.error("Error toggling lab status {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to toggle lab status", e);
        }
    }
    /**
     * Lấy danh sách setup steps của lab
     */
    public List<SetupStep> getLabSetupSteps(Integer labId) {
        try {
            return setupStepRepository.findByLabIdOrderByStepOrder(labId);
        } catch (Exception e) {
            log.error("Error fetching setup steps for lab {}: {}", labId, e.getMessage());
            throw new RuntimeException("Failed to fetch setup steps", e);
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
