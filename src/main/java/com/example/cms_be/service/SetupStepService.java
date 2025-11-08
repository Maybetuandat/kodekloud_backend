package com.example.cms_be.service;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.example.cms_be.model.Lab;
import com.example.cms_be.model.SetupStep;
import com.example.cms_be.repository.LabRepository;
import com.example.cms_be.repository.SetupStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SetupStepService {
    private final SetupStepRepository setupStepRepository;
    private final LabRepository labRepository;
    public List<SetupStep> getLabSetupSteps(Integer labId) {
        try {
            return setupStepRepository.findByLabIdOrderByStepOrder(labId);
        } catch (Exception e) {
            log.error("Error fetching setup steps for lab {}: {}", labId, e.getMessage());
            throw new RuntimeException("Failed to fetch setup steps", e);
        }
    }    
    public SetupStep createSetupStep(SetupStep setupStep, Integer labId) {
        try {
           if(labId == null ) {
                throw new IllegalArgumentException("Lab id cant be null");
            }
            Optional<Lab> labOpt = labRepository.findById(  labId);
            if (labOpt.isEmpty()) {
                throw new IllegalArgumentException("Dont find lab: " + labId);
            }
            Lab lab = labOpt.get();
            setupStep.setLab(lab);
            Integer nextOrder = getNextStepOrder(lab.getId());
            setupStep.setStepOrder(nextOrder);
            
            SetupStep createdStep = setupStepRepository.save(setupStep);
            log.info("Setup step created successfully with ID: {}", createdStep.getId());
            return createdStep;
        } catch (Exception e) {
            log.error("Error creating setup step: {}", e.getMessage());
            throw new RuntimeException("Failed to create setup step", e);
        }
    }
  
    public void swapOrderSetupStep(Integer fromStepId, Integer toStepId) {
        try {
            Optional<SetupStep> fromStepOpt = setupStepRepository.findById(fromStepId);
            Optional<SetupStep> toStepOpt = setupStepRepository.findById(toStepId);

            if (fromStepOpt.isEmpty() || toStepOpt.isEmpty()) {
                throw new IllegalArgumentException("One or both setup steps not found for swapping");
            }

            SetupStep fromStep = fromStepOpt.get();
            SetupStep toStep = toStepOpt.get();

            int tempOrder = fromStep.getStepOrder();
            fromStep.setStepOrder(toStep.getStepOrder());
            toStep.setStepOrder(tempOrder);

            setupStepRepository.save(fromStep);
            setupStepRepository.save(toStep);

            log.info("Swapped order of setup steps {} and {}", fromStepId, toStepId);
        } catch (Exception e) {
            log.error("Error swapping setup steps {} and {}: {}", fromStepId, toStepId, e.getMessage());
            throw new RuntimeException("Failed to swap setup steps", e);
        }
    }
    
    public SetupStep updateSetupStep( SetupStep setupStepUpdate, Integer setupStepId) {
        try {
            Optional<SetupStep> existingStepOpt = setupStepRepository.findById(setupStepId);
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
    
    public boolean deleteSetupStep(Integer id) {
        try {
            Optional<SetupStep> stepOpt = setupStepRepository.findById(id);
            if (stepOpt.isEmpty()) {
                return false;
            }
            SetupStep step = stepOpt.get();
            Integer labId = step.getLab().getId();
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
     private void reorderStepsAfterDeletion(Integer labId, int deletedOrder) {
       try {
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
       } catch (Exception e) {
        log.error("Error reordering steps after deletion in lab {}: {}", labId, e.getMessage());
        throw new RuntimeException("Failed to reorder steps after deletion", e);
       }
    }

   
    private Integer getNextStepOrder(Integer labId) {
    try {
            List<SetupStep> existingSteps = setupStepRepository.findByLabIdOrderByStepOrder(labId);
        return existingSteps.isEmpty() ? 1 : 
               existingSteps.get(existingSteps.size() - 1).getStepOrder() + 1;
    } catch (Exception e) {
        log.error("Error getting next step order for lab {}: {}", labId, e.getMessage());
        throw new RuntimeException("Failed to get next step order", e);
    }
    }


}
