package com.example.cms_be.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LabProvisionResponse {
    private Integer sessionId;
    private String status;
    private String message;
    private String podName;
    private String errorDetails;
}