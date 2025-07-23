package com.example.cms_be.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.cms_be.model.Lab;
import com.example.cms_be.model.SetupStep;
import com.example.cms_be.repository.LabRepository;
import com.example.cms_be.repository.SetupStepRepository;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SetupStepService {

    private final SetupStepRepository setupStepRepository;

    private final LabRepository labRepository;
   
    /**
     * Tạo mới setup step
     */
    @Transactional
    public SetupStep createSetupStep(SetupStep setupStep, String labId) {
        try {
            
            
            
           if(labId == null || labId.isEmpty()) {
                throw new IllegalArgumentException("Lab ID không được để trống");
            }

            Optional<Lab> labOpt = labRepository.findById(labId);
            if (labOpt.isEmpty()) {
                throw new IllegalArgumentException("Lab không tồn tại với ID: " + labId);
            }
            
            Lab lab = labOpt.get();
            setupStep.setLab(lab);

            if (setupStep.getStepOrder() == null) {
                int nextOrder = getNextStepOrder(lab.getId());
                setupStep.setStepOrder(nextOrder);
            }
            SetupStep createdStep = setupStepRepository.save(setupStep);
            log.info("Setup step created successfully with ID: {}", createdStep.getId());
            return createdStep;
        } catch (Exception e) {
            log.error("Error creating setup step: {}", e.getMessage());
            throw new RuntimeException("Failed to create setup step", e);
        }
    }
       
    /**
     * Tạo nhiều setup steps cùng lúc cho một lab cụ thể
     */
    @Transactional
    public List<SetupStep> createBatchSetupSteps(String labId, List<SetupStep> setupSteps) {
        try {
            if (setupSteps == null || setupSteps.isEmpty()) {
                throw new IllegalArgumentException("Danh sách setup steps không được để trống");
            }

            // Kiểm tra lab tồn tại
            Optional<Lab> labOpt = labRepository.findById(labId);
            if (labOpt.isEmpty()) {
                throw new IllegalArgumentException("Lab không tồn tại với ID: " + labId);
            }

            Lab lab = labOpt.get();
         

            for(int i=0; i < setupSteps.size(); i++)
            {
                setupSteps.get(i).setLab(lab);
                setupSteps.get(i).setStepOrder(i + 1); 
            }
            List<SetupStep> savedSteps = setupStepRepository.saveAll(setupSteps);
            log.info("Created {} setup steps successfully for lab {}", savedSteps.size(), labId);
            return savedSteps;
        } catch (Exception e) {
            log.error("Error creating batch setup steps for lab {}: {}", labId, e.getMessage());
            throw new RuntimeException("Failed to create batch setup steps", e);
        }
    }

   
    /**
     * Cập nhật setup step
     */
    @Transactional
    public SetupStep updateSetupStep( SetupStep setupStepUpdate) {
        try {
            Optional<SetupStep> existingStepOpt = setupStepRepository.findById(setupStepUpdate.getId());
            if (existingStepOpt.isEmpty()) {
                return null;
            }

            SetupStep existingStep = existingStepOpt.get();
            
            
            

           
            // Update fields
            if (setupStepUpdate.getStepOrder() != null &&  !setupStepUpdate.getStepOrder().equals(existingStep.getStepOrder())) {
                existingStep.setStepOrder(setupStepUpdate.getStepOrder());
            }
            existingStep.setTitle(setupStepUpdate.getTitle());
            existingStep.setDescription(setupStepUpdate.getDescription());
            existingStep.setSetupCommand(setupStepUpdate.getSetupCommand());
            
            if (setupStepUpdate.getExpectedExitCode() != null) {
                existingStep.setExpectedExitCode(setupStepUpdate.getExpectedExitCode());
            }
            if (setupStepUpdate.getRetryCount() != null) {
                existingStep.setRetryCount(setupStepUpdate.getRetryCount());
            }
            if (setupStepUpdate.getTimeoutSeconds() != null) {
                existingStep.setTimeoutSeconds(setupStepUpdate.getTimeoutSeconds());
            }
            if (setupStepUpdate.getContinueOnFailure() != null) {
                existingStep.setContinueOnFailure(setupStepUpdate.getContinueOnFailure());
            }

            SetupStep updatedStep = setupStepRepository.save(existingStep);
            log.info("Setup step updated successfully with ID: {}", updatedStep.getId());
            return updatedStep;
        } catch (Exception e) {
            log.error("Error updating setup step {}: {}", setupStepUpdate.getId(), e.getMessage());
            throw new RuntimeException("Failed to update setup step", e);
        }
    }

    /**
     * Xóa một setup step
     */
    @Transactional
    public boolean deleteSetupStep(String id) {
        try {
            Optional<SetupStep> stepOpt = setupStepRepository.findById(id);
            if (stepOpt.isEmpty()) {
                return false;
            }

            SetupStep step = stepOpt.get();
            String labId = step.getLab().getId();
            int deletedOrder = step.getStepOrder();

            // Xóa step
            setupStepRepository.delete(step);

            // Cập nhật lại step order cho các step sau
            reorderStepsAfterDeletion(labId, deletedOrder);

            log.info("Setup step deleted with ID: {}", id);
            return true;
        } catch (Exception e) {
            log.error("Error deleting setup step {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to delete setup step", e);
        }
    }
     private void reorderStepsAfterDeletion(String labId, int deletedOrder) {
        List<SetupStep> stepsToReorder = setupStepRepository.findByLabIdOrderByStepOrder(labId)
            .stream()
            .filter(step -> step.getStepOrder() > deletedOrder)
            .collect(Collectors.toList());

        for (SetupStep step : stepsToReorder) {
            step.setStepOrder(step.getStepOrder() - 1);
        }

        if (!stepsToReorder.isEmpty()) {
            setupStepRepository.saveAll(stepsToReorder);
            log.info("Reordered {} steps after deletion in lab {}", stepsToReorder.size(), labId);
        }
    }

     /**
     * Xóa nhiều setup steps cùng lúc
     */
    @Transactional
    public int deleteBatchSetupSteps(List<String> setupStepIds) {
        try {
            if (setupStepIds == null || setupStepIds.isEmpty()) {
                throw new IllegalArgumentException("Danh sách setup step IDs không được để trống");
            }

            // Lấy danh sách các steps cần xóa
            List<SetupStep> stepsToDelete = setupStepRepository.findAllById(setupStepIds);
            
            if (stepsToDelete.isEmpty()) {
                log.warn("Không tìm thấy setup steps nào với IDs: {}", setupStepIds);
                return 0;
            }

            // Group by lab để xử lý reorder
            Map<String, List<SetupStep>> stepsByLab = stepsToDelete.stream()
                .collect(Collectors.groupingBy(step -> step.getLab().getId()));

            // Xóa tất cả steps
            setupStepRepository.deleteAll(stepsToDelete);

            // Reorder lại cho từng lab
            for (Map.Entry<String, List<SetupStep>> entry : stepsByLab.entrySet()) {
                String labId = entry.getKey();
                List<Integer> deletedOrders = entry.getValue().stream()
                    .map(SetupStep::getStepOrder)
                    .sorted()
                    .collect(Collectors.toList());
                
                reorderStepsAfterBatchDeletion(labId, deletedOrders);
            }

            int deletedCount = stepsToDelete.size();
            log.info("Deleted {} setup steps successfully", deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("Error deleting batch setup steps: {}", e.getMessage());
            throw new RuntimeException("Failed to delete batch setup steps", e);
        }
    }
      private void reorderStepsAfterBatchDeletion(String labId, List<Integer> deletedOrders) {
        List<SetupStep> allSteps = setupStepRepository.findByLabIdOrderByStepOrder(labId);
        
        // Tính toán order mới cho từng step
        for (SetupStep step : allSteps) {
            int currentOrder = step.getStepOrder();
            long deletedBefore = deletedOrders.stream()
                .mapToInt(Integer::intValue)
                .filter(order -> order < currentOrder)
                .count();
            
            if (deletedBefore > 0) {
                step.setStepOrder(currentOrder - (int) deletedBefore);
            }
        }

        setupStepRepository.saveAll(allSteps);
        log.info("Reordered steps after batch deletion in lab {}", labId);
    }
    private int getNextStepOrder(String labId) {
        List<SetupStep> existingSteps = setupStepRepository.findByLabIdOrderByStepOrder(labId);
        return existingSteps.isEmpty() ? 1 : 
               existingSteps.get(existingSteps.size() - 1).getStepOrder() + 1;
    }


}
