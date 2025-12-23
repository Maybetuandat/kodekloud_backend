
package com.example.cms_be.service;

import com.example.cms_be.dto.course.DashboardDTO;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.UserLabSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Slf4j

public class DashboardService {

    private final UserLabSessionRepository userLabSessionRepository;

    public List<DashboardDTO> getLeaderboardByCourse(Integer courseId) {
        try {
            List<UserLabSession> sessions = userLabSessionRepository.findAllByCourseId(courseId);
            
            // Nhóm session theo User ID
            Map<Integer, List<UserLabSession>> sessionsByUser = sessions.stream()
                .collect(Collectors.groupingBy(s -> s.getCourseUser().getUser().getId()));
            
            List<DashboardDTO> leaderboard = sessionsByUser.entrySet().stream()
                .map(entry -> {
                    List<UserLabSession> userSessions = entry.getValue();
                    var user = userSessions.get(0).getCourseUser().getUser();
                    
                    int completedSessions = 0;
                    int totalSubmissions = 0;
                    String lastActivity = null;

                    for (UserLabSession session : userSessions) {
                        // 1. Tính số submission trong session này
                        int submissionCount = session.getSubmissions() != null ? session.getSubmissions().size() : 0;
                        totalSubmissions += submissionCount;
                        
                        // 2. Tính số câu hỏi của Lab trong session này
                        int questionCount = (session.getLab() != null && session.getLab().getLabQuestions() != null) ? 
                            session.getLab().getLabQuestions().size() : 0;
                        
                        // 3. Logic: Nếu submission == số câu hỏi -> Tính là làm đúng (completed)
                        if (questionCount > 0 && submissionCount == questionCount) {
                            completedSessions++;
                        }

                        // 4. Tìm ngày hoạt động cuối cùng
                        if (session.getCreatedAt() != null) {
                            String createdAt = session.getCreatedAt().toString();
                            if (lastActivity == null || createdAt.compareTo(lastActivity) > 0) {
                                lastActivity = createdAt;
                            }
                        }
                    }

                    int totalAttempts = userSessions.size();
                    double completionRate = totalAttempts > 0 ? (completedSessions * 100.0 / totalAttempts) : 0.0;

                    DashboardDTO dto = new DashboardDTO();
                    dto.setUserId(user.getId());
                    dto.setUsername(user.getUsername());
                    dto.setFullName(user.getLastName() + " " + user.getFirstName());
                    dto.setCompletedLabs(completedSessions);
                    dto.setTotalAttempts(totalAttempts);
                    dto.setTotalSubmissions(totalSubmissions);
                    dto.setTotalScore(completedSessions * 100); // Ví dụ mỗi bài đúng 100đ
                    dto.setCompletionRate(completionRate);
                    dto.setLastActivityAt(lastActivity);
                    
                    return dto;
                })
                // Sắp xếp theo số bài hoàn thành giảm dần, nếu bằng nhau thì theo số lần thử ít hơn
                .sorted(Comparator.comparing(DashboardDTO::getCompletedLabs).reversed()
                        .thenComparing(DashboardDTO::getTotalAttempts))
                .collect(Collectors.toList());

            // Đánh số thứ tự (Rank)
            for (int i = 0; i < leaderboard.size(); i++) {
                leaderboard.get(i).setRank(i + 1);
            }
            
            return leaderboard;
        } catch (Exception e) {
            log.error("Error generating leaderboard for course {}: {}", courseId, e.getMessage());
            throw new RuntimeException("Could not fetch leaderboard data");
        }
    }
}