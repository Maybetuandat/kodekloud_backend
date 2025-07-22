package com.example.cms_be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "setup_steps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupStep {

    @Id
    private String id;
    
    @Column(name = "step_order", nullable = false)
    private Integer stepOrder; 

    @Column(nullable = false)
    private String title; 

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "setup_command", columnDefinition = "TEXT", nullable = false)
    private String setupCommand;


    @Column(name = "expected_exit_code")
    private Integer expectedExitCode = 0;

     @Column(name = "retry_count")
    private Integer retryCount = 1;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds = 300;

    @Column(name = "continue_on_failure")
    private Boolean continueOnFailure = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Lab lab;
}
