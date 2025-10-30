package com.example.cms_be.dto;

import lombok.Data;

// DTO này sẽ chứa các thông tin cần thiết để hiển thị trên UI
@Data
public class BackingImageDTO {
    private String name;
    private String uuid;
    private long size; // Kích thước tính bằng byte
    private String state; // Ví dụ: "ready", "in-progress", "failed"
    private int downloadProgress; // Tiến độ tải về (%)
    private String createdFrom; // Nguồn gốc (download hoặc upload)
}