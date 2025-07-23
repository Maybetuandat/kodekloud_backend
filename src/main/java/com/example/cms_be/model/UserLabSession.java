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
@Table(name = "user_lab_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLabSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = true)
    private String id;

    //private CourseUser courseUser;
    //private SetupQuestionLog setupQuestionLog;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "setup_started_at")
    private LocalDateTime setupStartedAt;
    
    @Column(name = "setup_completed_at")
    private LocalDateTime setupCompletedAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "pod_name")
    private String podName;
    

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Lab lab;

    @JsonIgnore
    @OneToMany(mappedBy = "labSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SetupExecutionLog> setupExecutionLogs;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        
    }
}
