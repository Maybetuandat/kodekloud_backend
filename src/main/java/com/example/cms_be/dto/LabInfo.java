package com.example.cms_be.dto;


public record LabInfo(
        Integer id,
        String title,
        String description,
        Integer estimatedTime,
        String category
) {}