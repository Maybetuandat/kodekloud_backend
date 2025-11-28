package com.example.cms_be.service;

import com.example.cms_be.model.*;
import com.example.cms_be.repository.CourseLabRepository;
import com.example.cms_be.repository.CourseUserRepository;
import com.example.cms_be.repository.LabRepository;
import com.example.cms_be.repository.UserLabSessionRepository;
import com.example.cms_be.repository.UserRepository;
import io.kubernetes.client.openapi.ApiException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabSessionService {

    private final LabRepository labRepository;
    private final UserRepository userRepository;
    private final CourseUserRepository courseUserRepository;
    private final UserLabSessionRepository userLabSessionRepository;

    private final LabOrchestrationService orchestrationService;
    private final CourseLabRepository courseLabRepository;

    @Transactional
    public UserLabSession createAndStartSession(Integer labId, Integer userId) throws IOException, ApiException {
         try {
             Lab lab = labRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy Lab với ID: " + labId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy User với ID: " + userId));
        CourseLab courseLab = courseLabRepository.findByLabId(labId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy CourseLab với Lab ID: " + labId));
        boolean isEnrolled = courseUserRepository.existsByUserAndCourseId(user, courseLab.getCourse());
        if (!isEnrolled) {
            throw new AccessDeniedException("Người dùng chưa đăng ký khóa học này.");
        }

        // check exist session  if user has session for the lab then continue 
        List<String> activeStatuses = List.of("PENDING", "RUNNING");
        Optional<UserLabSession> existingSession = userLabSessionRepository.findActiveSessionByUserAndLab(userId, labId, activeStatuses);
        if (existingSession.isPresent()) {
            log.info("User {} already has an active session for lab {}. Returning existing session.", userId, labId);
            return existingSession.get();
        }

        UserLabSession session = new UserLabSession();
        session.setLab(lab);
        session.setSetupStartedAt(LocalDateTime.now());
        session.setStatus("PENDING");
        CourseUser courseUser = courseUserRepository.findByUserAndCourse(user, courseLab.getCourse())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bản ghi đăng ký khóa học (CourseUser) tương ứng."));
        session.setCourseUser(courseUser);

        UserLabSession savedSession = userLabSessionRepository.save(session);
        log.info("Created UserLabSession {} for user {}", savedSession.getId(), userId);

        // 🔥 FIX: Use new eager loading method instead of direct async call
        orchestrationService.provisionAndSetupLabWithEagerLoading(savedSession);

        return savedSession;
         } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo và khởi động phiên lab: " + e.getMessage(), e);
         }
    }

    @Transactional 
    public void submitSession(Integer labSessionId) {
        try {
            log.info("Submitting session {}...", labSessionId);

        UserLabSession session = userLabSessionRepository.findById(labSessionId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy UserLabSession với ID: " + labSessionId));

        session.setStatus("COMPLETED");
        session.setExpiresAt(LocalDateTime.now());
        userLabSessionRepository.save(session);
        log.info("Session {} status updated to COMPLETED.", labSessionId);

        // 2. Kích hoạt "nhạc trưởng" để dọn dẹp tài nguyên K8s chạy ngầm
        orchestrationService.cleanupLabResources(session);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi gửi phiên lab: " + e.getMessage(), e);
        }
    }
}