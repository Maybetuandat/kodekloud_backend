// Service
package com.example.cms_be.service;


import com.example.cms_be.dto.course.LeaderboardEntryDTO;
import com.example.cms_be.repository.UserLabSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardService {

    private final UserLabSessionRepository userLabSessionRepository;

    public List<LeaderboardEntryDTO> getLeaderboardByCourse(Integer courseId) {
        try {
            List<Object[]> results = userLabSessionRepository.findLeaderboardByCourseId(courseId);
            
            return IntStream.range(0, results.size())
                .mapToObj(index -> {
                    Object[] row = results.get(index);
                    Integer rank = index + 1;
                    Integer userId = (Integer) row[0];
                    String username = (String) row[1];
                    String fullName = (String) row[2];
                    Long completedLabs = (Long) row[3];
                    Long totalLabs = (Long) row[4];
                    Double averageTime = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;
                    Long totalTime = row[6] != null ? ((Number) row[6]).longValue() : 0L;
                    String lastActivityAt = row[7] != null ? row[7].toString() : null;
                    
                    Integer completedLabsInt = completedLabs != null ? completedLabs.intValue() : 0;
                    Integer totalLabsInt = totalLabs != null ? totalLabs.intValue() : 0;
                    Integer totalScore = completedLabsInt * 100;
                    Double completionRate = totalLabsInt > 0 
                        ? (completedLabsInt * 100.0 / totalLabsInt) 
                        : 0.0;
                    
                    return new LeaderboardEntryDTO(
                        rank,
                        userId,
                        username,
                        fullName,
                        totalScore,
                        completedLabsInt,
                        totalLabsInt,
                        completionRate,
                        averageTime,
                        totalTime.intValue(),
                        0,
                        0,
                        lastActivityAt
                    );
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching leaderboard for course {}: {}", courseId, e.getMessage());
            throw new RuntimeException("Failed to fetch leaderboard", e);
        }
    }
}