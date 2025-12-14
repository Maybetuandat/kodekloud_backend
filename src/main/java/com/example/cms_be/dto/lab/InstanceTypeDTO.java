package com.example.cms_be.dto.lab;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InstanceTypeDTO {
    private String backingImage;
    private Integer cpuCores;
    private Integer memoryGb;
    private Integer storageGb;
}