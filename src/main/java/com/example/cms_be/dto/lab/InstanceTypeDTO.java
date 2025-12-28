package com.example.cms_be.dto.lab;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InstanceTypeDTO {
    private String backingImage;
    private Integer cpuCores;
    private Integer memoryGb;
    private Integer storageGb;
}