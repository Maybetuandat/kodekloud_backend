package com.example.cms_be.dto.lab;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabTestResponse {
    
    private String testId; 
    private Integer labId;
    private String testVmName; 
    private String status; 
    private String websocketUrl; 
    private LocalDateTime startedAt;
    private Map<String, Object> connectionInfo; 
    
    
    public LabTestResponse(String testId, Integer labId, String testVmName, String websocketUrl, Map<String, Object> connectionInfo) {
        this.testId = testId;
        this.labId = labId;
        this.testVmName = testVmName;
        this.status = "STARTING";
        this.websocketUrl = websocketUrl;
        this.startedAt = LocalDateTime.now();
        this.connectionInfo = connectionInfo;
    }
}
