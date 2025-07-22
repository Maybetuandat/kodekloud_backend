package com.example.cms_be.model;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "lab_templates")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lab {

    @Id
    private String id; 

    private String name; 

    private String description;

    @Column(name = "base_image", nullable = false)
    private String baseImage;

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
