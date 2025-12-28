package com.example.cms_be.dto.lab;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LabTestRequest {
    private Integer labId;
    private String testVmName;
    private String namespace;
    private String labTitle;
    private InstanceTypeDTO instanceType;
    private String setupStepsJson;
}