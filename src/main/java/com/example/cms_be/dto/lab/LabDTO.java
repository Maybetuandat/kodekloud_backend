package com.example.cms_be.dto.lab;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabDTO {
    private Integer id;
    private String title;
    private String description;
    private Integer estimatedTime;
    private String namespace;
    private Boolean isActive;    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}