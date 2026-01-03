package com.example.cms_be.dto.lab;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabSessionReadyEvent {
    private Integer labSessionId;
    private String vmName;
    private String podName;
    private Integer labId;
    private Integer estimatedTimeMinutes;
}