
package com.example.cms_be.dto.course;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryDTO {
    private Integer rank;
    private Integer userId;
    private String username;
    private String fullName;
    private Integer totalScore;
    private Integer completedLabs;
    private Integer totalLabs;
    private Double completionRate;
    private Double averageTime;
    private Integer totalTime;
    private Integer firstTimeCompletions;
    private Integer fastCompletions;
    private String lastActivityAt;
}