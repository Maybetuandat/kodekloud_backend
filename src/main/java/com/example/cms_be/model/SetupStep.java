package com.example.cms_be.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.*;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, unique = true)
    private Integer id;
    
    @Column(name = "step_order", nullable = false)
    private Integer stepOrder; 

    @NotBlank(message = "Tiêu đề không được để trống")
    @Column(nullable = false)
    private String title; 

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotBlank(message = "Setup command không được để trống")
    @Column(name = "setup_command", columnDefinition = "TEXT", nullable = false)
    private String setupCommand;


    @Column(name = "expected_exit_code")
    private Integer expectedExitCode = 0;

     @Column(name = "retry_count")
    private Integer retryCount = 1;

    @Min(value = 1, message = "Timeout phải >= 1 giây")
    @Max(value = 3600, message = "Timeout không được vượt quá 3600 giây (1 giờ)")
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds = 300;

    @Column(name = "continue_on_failure")
    private Boolean continueOnFailure = false;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Lab lab;


  
}
