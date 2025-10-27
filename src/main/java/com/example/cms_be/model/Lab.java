package com.example.cms_be.model;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "labs")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, unique = true)
    private Integer id;

    @Column(name = "title", nullable = false)
    private String title;

    private String description;

   

    @Column(name = "namespace", nullable = true)
    private String namespace;

    @Column(name = "storage", nullable = true)
    private String storage;

    @Column(name = "memory", nullable = true)
    private String memory;

    
    
    
    @Column(name = "estimated_time", nullable = true)
    private Integer estimatedTime;

    @Column(name = "is_active", nullable = true)
    private Boolean isActive;

    @Column(name = "created_at", nullable = true, updatable = false)
    private LocalDateTime createdAt;

  
    @JsonIgnore
    @OneToMany(mappedBy = "lab", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CourseLab> courseLabs;

    @JsonIgnore
    @OneToMany(mappedBy = "lab", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SetupStep> setupSteps;

    @JsonIgnore
    @OneToMany(mappedBy = "lab", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserLabSession> userLabSessions;



    @OneToMany(mappedBy = "lab", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Question> labQuestions;
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
