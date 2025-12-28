package com.example.cms_be.dto.lab;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateLabRequest{


    private String title;
    private String description;
    private Integer estimatedTime;
    private Integer instanceTypeId;
    private Boolean isActive;
}


   
 
