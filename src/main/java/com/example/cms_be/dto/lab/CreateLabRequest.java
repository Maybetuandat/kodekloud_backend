package com.example.cms_be.dto.lab;

import lombok.Data;

@Data
public class CreateLabRequest{


    private String title;
    private String description;
    private Integer estimatedTime;
    private String backingImage;
    private Integer instanceTypeId;
    private Boolean isActive;
    private Integer categoryId;
}


   
 
