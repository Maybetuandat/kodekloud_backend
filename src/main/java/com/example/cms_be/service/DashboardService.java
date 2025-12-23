package com.example.cms_be.service;

import com.example.cms_be.dto.course.DashboardDTO;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.SubmissionRepository;
import com.example.cms_be.repository.UserLabSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {
    
    private final UserLabSessionRepository sessionRepository;
    private final SubmissionRepository submissionRepository;
    
    public List<DashboardDTO> getDashboard(Integer courseId) {
        List<UserLabSession> sessions = sessionRepository.findByCourseIdWithUser(courseId);
        
        Map<Integer, DashboardDTO> dashboardMap = new HashMap<>();
        
        for (UserLabSession session : sessions) {
            Integer userId = session.getCourseUser().getUser().getId();
            
            if (!dashboardMap.containsKey(userId)) {
                DashboardDTO dto = new DashboardDTO();
                dto.setUserId(userId);
                dto.setUsername(session.getCourseUser().getUser().getUsername());
                dto.setFullName(session.getCourseUser().getUser().getFirstName() + " " + 
                               session.getCourseUser().getUser().getLastName());
                dto.setTotalScore(0);
                dto.setCompletedLabs(0);
                dto.setTotalAttempts(0);
                dto.setTotalSubmissions(0);
                dto.setCompletionRate(0.0);
                dto.setLastActivityAt(null);
                
                dashboardMap.put(userId, dto);
            }
            
            DashboardDTO dto = dashboardMap.get(userId);
            dto.setTotalAttempts(dto.getTotalAttempts() + 1);
            
            if ("COMPLETED".equals(session.getStatus())) {
                int correctCount = submissionRepository.countCorrectBySessionId(session.getId());
                dto.setTotalScore(dto.getTotalScore() + correctCount * 10);
                dto.setCompletedLabs(dto.getCompletedLabs() + 1);
            }
        }
        
        for (DashboardDTO dto : dashboardMap.values()) {
            Integer userId = dto.getUserId();
            
            int totalSubmissions = submissionRepository.countTotalByUserAndCourse(userId, courseId);
            dto.setTotalSubmissions(totalSubmissions);
            
            int completedLabs = sessionRepository.countCompletedByUserAndCourse(userId, courseId);
            int totalAttempts = sessionRepository.countTotalByUserAndCourse(userId, courseId);
            
            double completionRate = totalAttempts > 0 ? (completedLabs * 100.0 / totalAttempts) : 0.0;
            dto.setCompletionRate(Math.round(completionRate * 100.0) / 100.0);
            
            var lastActivity = sessionRepository.findLastActivityByUserAndCourse(userId, courseId);
            if (lastActivity != null) {
                dto.setLastActivityAt(lastActivity.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
        }
        
        List<DashboardDTO> sortedList = dashboardMap.values().stream()
            .sorted(Comparator.comparing(DashboardDTO::getTotalScore).reversed()
                    .thenComparing(DashboardDTO::getCompletedLabs).reversed()
                    .thenComparing(DashboardDTO::getCompletionRate).reversed())
            .collect(Collectors.toList());
        
        for (int i = 0; i < sortedList.size(); i++) {
            sortedList.get(i).setRank(i + 1);
        }
        
        return sortedList;
    }
}