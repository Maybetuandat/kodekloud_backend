package com.example.cms_be.model;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "setup_step_questions")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupStepQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "setup_command", nullable = false, columnDefinition = "TEXT")
    private String setupCommand;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = true)
    private Question question;

    @Column(name = "created_at",updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;


    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;

   
}

