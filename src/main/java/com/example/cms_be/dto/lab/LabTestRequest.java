package com.example.cms_be.dto.lab;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabTestRequest {
    
    @NotNull(message = "Lab ID is required")
    private Integer labId;
    
    
    private String namespace;
    
    
    private Integer timeoutSeconds = 1800; 
}