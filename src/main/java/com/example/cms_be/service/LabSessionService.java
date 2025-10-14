package com.example.cms_be.service;

import com.example.cms_be.model.*;
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
    private final VMService vmService;

    @Transactional
    public UserLabSession createAndStartSession(Integer labId, Integer userId) throws IOException, ApiException {
        Lab lab = labRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy Lab với ID: " + labId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy User với ID: " + userId));

        boolean isEnrolled = courseUserRepository.existsByUserAndCourseId(user, lab.getCourse());
        if (!isEnrolled) {
            throw new AccessDeniedException("Người dùng chưa đăng ký khóa học này.");
        }

        // check exist session
        List<String> activeStatuses = List.of("PENDING", "RUNNING");
        Optional<UserLabSession> existingSession = userLabSessionRepository
                .findActiveSessionByUserAndLab(userId, labId, activeStatuses);
        if (existingSession.isPresent()) {
            log.info("User {} already has an active session for lab {}. Returning existing session.", userId, labId);
            return existingSession.get();
        }

        UserLabSession session = new UserLabSession();
        session.setLab(lab);

        session.setSetupStartedAt(LocalDateTime.now());
        session.setStatus("PENDING");
        CourseUser courseUser = courseUserRepository.findByUserAndCourse(user, lab.getCourse())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bản ghi đăng ký khóa học (CourseUser) tương ứng."));

        session.setCourseUser(courseUser);

        UserLabSession savedSession = userLabSessionRepository.save(session);
        log.info("Created UserLabSession {} for user {}", savedSession.getId(), userId);

        vmService.provisionVmForSession(savedSession);

        return savedSession;
    }
}