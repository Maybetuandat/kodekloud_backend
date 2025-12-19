package com.example.cms_be.controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.cms_be.model.InstanceType;
import com.example.cms_be.service.InstanceTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;






@RestController
@RequestMapping("/api/instance-types")
@Slf4j
@RequiredArgsConstructor
public class InstanceTypeController {


      
    private final InstanceTypeService instanceTypeService;
    @GetMapping("")
    public ResponseEntity<List<InstanceType>> getAllInstanceType() {
        try {
            List<InstanceType> instanceTypes = instanceTypeService.getAllInstanceTypes();
            return ResponseEntity.ok(instanceTypes);
        } catch (Exception e) {
                log.error("Error fetching all InstanceTypes: {}", e.getMessage());
                return ResponseEntity.status(500).build();
        }
    }

    
    @GetMapping("{instanceTypeId}")
    public ResponseEntity<InstanceType> getInstanceTypeById(@PathVariable Integer instanceTypeId) {
        try {
            InstanceType instanceType = instanceTypeService.getInstanceTypeById(instanceTypeId);
            return ResponseEntity.ok(instanceType);
        } catch (Exception e) {
            log.error("Error fetching InstanceType with ID {}: {}", instanceTypeId, e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("")
    public ResponseEntity<InstanceType> createInstanceType(@RequestBody InstanceType instanceType) {
        try {
            InstanceType createdInstanceType = instanceTypeService.createInstanceType(instanceType);
            return ResponseEntity.status(201).body(createdInstanceType);
        } catch (Exception e) {
            log.error("Error creating InstanceType: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }


    @PatchMapping("{instanceTypeId}")
    public ResponseEntity<InstanceType> updateInstanceType(@PathVariable Integer instanceTypeId,
                                                           @RequestBody InstanceType updatedInstanceType) {
        try {
            InstanceType instanceType = instanceTypeService.updateInstanceType(instanceTypeId, updatedInstanceType);
            return ResponseEntity.ok(instanceType);
        } catch (Exception e) {
            log.error("Error updating InstanceType with ID {}: {}", instanceTypeId, e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("{instanceTypeId}")
    public ResponseEntity<Void> deleteInstanceType(@PathVariable Integer instanceTypeId) {
        try {
            instanceTypeService.deleteInstanceType(instanceTypeId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting InstanceType with ID {}: {}", instanceTypeId, e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }
}
