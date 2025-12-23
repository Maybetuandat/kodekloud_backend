
package com.example.cms_be.dto.course;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {
    private Integer rank;
    private Integer userId;
    private String username;
    private String fullName;
    private Integer totalScore;        // Thường là completedLabs * 100
    private Integer completedLabs;     // Số session làm đúng (submissions == questions)
    private Integer totalAttempts;     // Tổng số lần thực hiện bài thực hành
    private Integer totalSubmissions;  // Tổng số submission đã nộp
    private Double completionRate;     
    private String lastActivityAt;
    
    // Các trường bổ sung nếu bạn cần dùng sau này
    private Double averageTime;
    private Integer totalLabs; 
}