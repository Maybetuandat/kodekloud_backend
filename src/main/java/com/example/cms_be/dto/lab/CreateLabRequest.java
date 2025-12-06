package com.example.cms_be.dto.lab;

import lombok.Data;

@Data
public class CreateLabRequest{


    private String title;
    private String description;
    private Integer estimatedTime;
    private Integer instanceTypeId;
    private Boolean isActive;
}


   
 
