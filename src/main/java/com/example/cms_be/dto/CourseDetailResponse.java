package com.example.cms_be.dto;


import lombok.Data;
import java.util.List;

import com.example.cms_be.dto.user.UserDTO;

@Data
public class CourseDetailResponse {
    private Integer id;
    private String title;
    private String description;
    private String shortDescription;
    private String level;
    private List<LabInfo> labs;
    private String category;
    private Integer studentsCount;
    private String updatedAt;
    private UserDTO lecturer;
}