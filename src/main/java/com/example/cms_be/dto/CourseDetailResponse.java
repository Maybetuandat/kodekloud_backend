package com.example.cms_be.dto;


import lombok.Data;
import java.util.List;

@Data
public class CourseDetailResponse {
    private Integer id;
    private String title;
    private String description;

    private List<LabInfo> labs;
}