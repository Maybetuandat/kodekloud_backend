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
@Table(name = "setup_step_questions")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupStepQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "Thứ tự bước không được null")
    @Min(value = 1, message = "Thứ tự bước phải từ 1 trở lên")
    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @NotBlank(message = "Lệnh setup không được để trống")
    @Column(name = "setup_command", nullable = false, columnDefinition = "TEXT")
    private String setupCommand;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

