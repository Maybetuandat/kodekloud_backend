package com.example.cms_be.model;

import java.time.LocalDateTime;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@Table(name = "setup_execution_logs")
@NoArgsConstructor
@AllArgsConstructor
public class SetupExecutionLog {

    @Id
    private String id;
    
    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;
    
    @Column(name = "step_title")
    private String stepTitle;
    
    @Column(columnDefinition = "TEXT")
    private String command;

    @Column(columnDefinition = "TEXT")
    private String output;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "exit_code")
    private Integer exitCode;
    
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;


     @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "setup_step_id", nullable = false)
    private SetupStep setupStep;



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_lab_session_id", nullable = false)
    private UserLabSession labSession;

     @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
       
    }

}
