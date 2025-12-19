package com.example.cms_be.dto.lab;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.cms_be.dto.lab.InstanceTypeDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLabSessionRequest {
    private Integer labSessionId;
    private String vmName;
    private String namespace;
    private Integer labId;
    private InstanceTypeDTO instanceType;
    private String setupStepsJson;
}