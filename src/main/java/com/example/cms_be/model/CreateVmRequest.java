package com.example.cms_be.model;

public record CreateVmRequest(
        String name,      // Tên của máy ảo và DataVolume
        String namespace, // Namespace để tạo tài nguyên
        String imageUrl,  // URL của cloud image, ví dụ: "https://cloud-images.ubuntu.com/..."
        String storage,   // Dung lượng lưu trữ, ví dụ: "10Gi"
        String memory     // Dung lượng RAM, ví dụ: "1Gi"
) {}
