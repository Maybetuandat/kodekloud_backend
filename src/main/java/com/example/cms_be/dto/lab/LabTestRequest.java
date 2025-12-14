package com.example.cms_be.dto.lab;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LabTestRequest {
    private Integer labId;
    private String testVmName;
    private String namespace;
    private String labTitle;
    private InstanceTypeDTO instanceType;
}