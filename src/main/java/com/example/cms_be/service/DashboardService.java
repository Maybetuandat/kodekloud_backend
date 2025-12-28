package com.example.cms_be.service;

import com.example.cms_be.dto.course.DashboardDTO;
import com.example.cms_be.model.CourseUser;
import com.example.cms_be.model.User;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.CourseUserRepository;
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
    
    private final CourseUserRepository courseUserRepository; 
    private final UserLabSessionRepository sessionRepository;
    private final SubmissionRepository submissionRepository;
    
    public List<DashboardDTO> getDashboard(Integer courseId) {
        
        List<CourseUser> enrolledUsers = courseUserRepository.findByCourseId(courseId);
        
        Map<Integer, DashboardDTO> dashboardMap = new HashMap<>();
        
        for (CourseUser cu : enrolledUsers) {
            User user = cu.getUser();
            DashboardDTO dto = new DashboardDTO();
            dto.setUserId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setFullName(user.getFirstName() + " " + user.getLastName());
            
        
            dto.setTotalScore(0);
            dto.setCompletedLabs(0);
            dto.setTotalAttempts(0);
            
            dto.setCompletionRate(0.0);
            dto.setLastActivityAt(null);
            
            dashboardMap.put(user.getId(), dto);
        }
        
   
        List<UserLabSession> sessions = sessionRepository.findByCourseIdWithUser(courseId);
        
        for (UserLabSession session : sessions) {
            Integer userId = session.getCourseUser().getUser().getId();
            DashboardDTO dto = dashboardMap.get(userId);
            
            if (dto != null) {
                dto.setTotalAttempts(dto.getTotalAttempts() + 1);
                
                if ("COMPLETED".equals(session.getStatus())) {
                    int correctCount = submissionRepository.countCorrectBySessionId(session.getId());
                    dto.setTotalScore(dto.getTotalScore() + correctCount * 10);  // 1 cau hop = 10 diem
                    dto.setCompletedLabs(dto.getCompletedLabs() + 1);
                }
            }
        }
        
     
        for (DashboardDTO dto : dashboardMap.values()) {
            Integer userId = dto.getUserId();
            
            
            
            
            double completionRate = dto.getTotalAttempts() > 0 
                ? (dto.getCompletedLabs() * 100.0 / dto.getTotalAttempts()) 
                : 0.0;
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