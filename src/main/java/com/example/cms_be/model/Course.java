package com.example.cms_be.model;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(name = "courses")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, unique = true)
    private Integer id;

    @Column(name = "title")
    @NotBlank(message = "title không được để trống")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "level")
    private String level;

    @Column(name = "duration_minutes")

    private Integer durationMinutes;  

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "short_description")
    private String shortDescription;

    @Column(name = "is_active")
    private Boolean isActive;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CourseUser> listCourseUser;
}
