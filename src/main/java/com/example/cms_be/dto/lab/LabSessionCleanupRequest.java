package com.example.cms_be.dto.lab;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabSessionCleanupRequest {
    private Integer labSessionId;
    private String vmName;
    private String namespace;
}
