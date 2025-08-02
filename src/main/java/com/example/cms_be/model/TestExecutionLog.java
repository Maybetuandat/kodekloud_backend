// package com.example.cms_be.model;

// import jakarta.persistence.*;
// import jakarta.persistence.GeneratedValue;
// import jakarta.persistence.Id;
// import jakarta.persistence.Table;
// import lombok.AllArgsConstructor;
// import lombok.Builder;
// import lombok.Data;
// import lombok.NoArgsConstructor;

// @Data
// @Builder
// @Entity
// @Table(name = "test_execution_log")
// @AllArgsConstructor
// @NoArgsConstructor
// public class TestExecutionLog {

//     @Id
//     @GeneratedValue(strategy = GenerationType.UUID)
//     private String id;

//     private String output;
//     private String errorMessage;
//     private String executionStatus;
//     private String executionTimeMs;

//     @ManyToOne
//     @JoinColumn(name = "test_execution_id", nullable = false)
//     private TestExecution testExecution;
// }
