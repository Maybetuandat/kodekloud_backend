// package com.example.cms_be.model;

// import java.time.LocalDateTime;
// import java.util.List;

// import jakarta.persistence.*;
// import lombok.AllArgsConstructor;
// import lombok.Builder;
// import lombok.Data;
// import lombok.NoArgsConstructor;

// @Entity
// @Data
// @AllArgsConstructor
// @NoArgsConstructor
// @Builder
// @Table(name = "test_execution")
// public class TestExecution {

//     @Id
//     @GeneratedValue(strategy = GenerationType.UUID)
//     private String id;

//     private LocalDateTime createAt;
    
//     private LocalDateTime expiredAt;

//     @OneToMany(mappedBy = "testExecution", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
//     private List<TestExecutionLog> listTestExecutionLogs;

    
//     @PrePersist
//     protected void onCreate() {
//         if (createAt == null) {
//             createAt = LocalDateTime.now();
//         }
        
//     }
// }
