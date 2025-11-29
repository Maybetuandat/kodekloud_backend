package com.example.cms_be.dto.course;

import lombok.Data;

@Data
public class CreateCourseRequest {

    private String title;
    private String description;
    private String shortDescription;
    private String level;
    private Boolean isActive;
    private Integer subjectId;
}
