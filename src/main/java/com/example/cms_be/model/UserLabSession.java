package com.example.cms_be.model;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
@EntityListeners(AuditingEntityListener.class)
public class UserLabSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, unique = true)
    private Integer id;

   

    @Column(name = "created_at")
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "setup_started_at")
    private LocalDateTime setupStartedAt;

    @Column(name = "setup_completed_at")
    private LocalDateTime setupCompletedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "pod_name")
    private String podName;

    @Column(name = "status")
    private String status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lab_id", nullable = true)
    private Lab lab;



    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_user_id", nullable = true)
    private CourseUser courseUser;




    @OneToMany(mappedBy = "userLabSession")
    private Set<Submission> submissions; 


  
}
