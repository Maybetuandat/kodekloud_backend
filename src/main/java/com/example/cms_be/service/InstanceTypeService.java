package com.example.cms_be.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.cms_be.model.InstanceType;
import com.example.cms_be.repository.InstanceTypeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstanceTypeService {

    private final InstanceTypeRepository instanceTypeRepository;


    public InstanceType getInstanceTypeById(Integer id) {
        try {

            InstanceType instanceType = instanceTypeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("InstanceType not found with id: " + id));
            return instanceType;
        } catch (Exception e) {
            log.error("Error fetching InstanceType by ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to fetch InstanceType", e);
        }
    }
    

    public List<InstanceType> getAllInstanceTypes() {
        try {
            return instanceTypeRepository.findAll();
        } catch (Exception e) {
            log.error("Error fetching all InstanceTypes: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch InstanceTypes", e);
        }
    }
    public InstanceType createInstanceType(InstanceType instanceType) {
        try {
            return instanceTypeRepository.save(instanceType);
        } catch (Exception e) {
            log.error("Error creating InstanceType: {}", e.getMessage());
            throw new RuntimeException("Failed to create InstanceType", e);
        }
    }
    public InstanceType updateInstanceType(Integer id, InstanceType updatedInstanceType) {
        try {
            InstanceType existingInstanceType = instanceTypeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("InstanceType not found with id: " + id));

            if(updatedInstanceType.getName() != null)
            {
                existingInstanceType.setName(updatedInstanceType.getName());
            }
            if(updatedInstanceType.getDescription() != null)
            {
                existingInstanceType.setDescription(updatedInstanceType.getDescription());
            }
            if(updatedInstanceType.getCpuCores() != null)
            {
                existingInstanceType.setCpuCores(updatedInstanceType.getCpuCores());
            }
            if(updatedInstanceType.getMemoryGb() != null)
            {
                existingInstanceType.setMemoryGb(updatedInstanceType.getMemoryGb());
            }
            if(updatedInstanceType.getStorageGb() != null)
            {
                existingInstanceType.setStorageGb(updatedInstanceType.getStorageGb());
            }
            if(updatedInstanceType.getBackingImage() != null)
            {
                existingInstanceType.setBackingImage(updatedInstanceType.getBackingImage());
            }

            return instanceTypeRepository.save(existingInstanceType);
        } catch (Exception e) {
            log.error("Error updating InstanceType with ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to update InstanceType", e);
        }
    }
    public void deleteInstanceType(Integer id) {
        try {
            instanceTypeRepository.deleteById(id);
        } catch (Exception e) {
            log.error("Error deleting InstanceType with ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to delete InstanceType", e);
        }
    }

}
