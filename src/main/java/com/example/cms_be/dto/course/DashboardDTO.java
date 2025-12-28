
package com.example.cms_be.dto.course;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {
    private Integer rank;
    private Integer userId;
    private String username;
    private String fullName;
    private Integer totalScore;   
    private Integer completedLabs;
    private Integer totalAttempts;
    private Integer totalSubmissions; 
    private Double completionRate;     
    private String lastActivityAt;
}