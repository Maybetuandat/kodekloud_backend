package com.example.cms_be.model;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "lab")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lab {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = true)
    private String id; 

    @NotBlank(message = "Tên lab không được để trống")
    private String name; 

    private String description;

    @NotBlank(message = "Base image không được để trống")
    @Column(name = "base_image", nullable = false)
    private String baseImage;

    @NotNull(message = "Thời gian ước tính không được null, đơn vị tính là phút")
    @Min(value = 1, message = "Thời gian ước tính phải ít nhất 1 phút")
    @Max(value = 600, message = "Thời gian ước tính không được vượt quá 600 phút")

    @Column(name = "estimated_time", nullable = false)
    private Integer estimatedTime;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    @OneToMany(mappedBy = "lab", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SetupStep> setupSteps;



    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isActive == null) {
            isActive = true;
        }
    }
}
