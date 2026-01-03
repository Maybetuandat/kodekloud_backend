package com.example.cms_be.service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.Optional;

import com.example.cms_be.dto.LabSessionHistoryResponse;
import com.example.cms_be.dto.lab.LabSessionCleanupRequest;
import com.example.cms_be.kafka.LabSessionCleanupProducer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cms_be.model.CourseLab;
import com.example.cms_be.model.CourseUser;
import com.example.cms_be.model.Lab;
import com.example.cms_be.model.User;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.repository.CourseLabRepository;
import com.example.cms_be.repository.CourseUserRepository;
import com.example.cms_be.repository.LabRepository;
import com.example.cms_be.repository.UserLabSessionRepository;
import com.example.cms_be.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserLabSessionService {

    private final LabRepository labRepository;
    private final UserRepository userRepository;
    private final CourseUserRepository courseUserRepository;
    private final UserLabSessionRepository userLabSessionRepository;
    private final LabOrchestrationService orchestrationService;
    private final CourseLabRepository courseLabRepository;
    private final String COMPLETED_STATUS = "COMPLETED";
    private final String RUNNING_STATUS = "RUNNING";
    private final LabSessionCleanupProducer cleanupProducer;

    public Page<UserLabSession> getUserLabSessionPagination(Integer userId, String keyword, Pageable pageable) {
        return userLabSessionRepository.findByUserIdAndKeyword(userId, keyword, pageable);
    }

    public UserLabSession createAndStartSession(Integer labId, Integer userId) throws IOException {
        try {
            Lab lab = labRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Khong tim thay Lab voi ID: " + labId));
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Khong tim thay User voi ID: " + userId));
            CourseLab courseLab = courseLabRepository.findByLabId(labId)
                .orElseThrow(() -> new EntityNotFoundException("Khong tim thay CourseLab voi Lab ID: " + labId));
            boolean isEnrolled = courseUserRepository.existsByUserAndCourseId(user, courseLab.getCourse());
            if (!isEnrolled) {
                throw new AccessDeniedException("Nguoi dung chua dang ky khoa hoc nay.");
            }

            Optional<UserLabSession> existingSession = userLabSessionRepository.findNonCompletedSessionByUserAndLab(userId, labId, COMPLETED_STATUS);
            if (existingSession.isPresent()) {
                log.warn("User {} da co session chua hoan thanh cho lab {}. Tu choi tao moi.", userId, labId);
                throw new IllegalStateException("Ban dang co mot phien lab chua hoan thanh. Vui long hoan thanh hoac huy phien truoc khi tao moi.");
            }

            UserLabSession session = new UserLabSession();
            session.setLab(lab);
            session.setSetupStartedAt(LocalDateTime.now());
            session.setStatus("PENDING");
            CourseUser courseUser = courseUserRepository.findByUserAndCourse(user, courseLab.getCourse())
                .orElseThrow(() -> new EntityNotFoundException("Khong tim thay ban ghi dang ky khoa hoc (CourseUser) tuong ung."));
            session.setCourseUser(courseUser);

            UserLabSession savedSession = userLabSessionRepository.save(session);
            log.info("Created UserLabSession {} for user {}", savedSession.getId(), userId);

            orchestrationService.provisionAndSetupLabWithEagerLoading(savedSession);

            return savedSession;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Loi khi tao va khoi dong phien lab: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void activateSession(Integer labSessionId, String podName) {
        try {
            log.info("Activating session {} with podName {}", labSessionId, podName);

            UserLabSession session = userLabSessionRepository.findById(labSessionId)
                .orElseThrow(() -> new EntityNotFoundException("Khong tim thay UserLabSession voi ID: " + labSessionId));

            session.setStatus(RUNNING_STATUS);
            session.setPodName(podName);
            session.setSetupCompletedAt(LocalDateTime.now());

            Integer estimatedTimeMinutes = session.getLab().getEstimatedTime();
            if (estimatedTimeMinutes != null && estimatedTimeMinutes > 0) {
                LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(estimatedTimeMinutes);
                session.setExpiresAt(expiresAt);
                log.info("Session {} expires at: {}", labSessionId, expiresAt);
            }

            userLabSessionRepository.save(session);
            log.info("Session {} activated successfully. Status: {}, PodName: {}, ExpiresAt: {}", 
                labSessionId, session.getStatus(), session.getPodName(), session.getExpiresAt());

        } catch (Exception e) {
            log.error("Error activating session {}: {}", labSessionId, e.getMessage(), e);
            throw new RuntimeException("Loi khi kich hoat phien lab: " + e.getMessage(), e);
        }
    }

    public void submitSession(Integer labSessionId) {
        try {
            log.info("Submitting session {}...", labSessionId);

            UserLabSession session = userLabSessionRepository.findById(labSessionId)
                    .orElseThrow(() -> new EntityNotFoundException("Khong tim thay UserLabSession voi ID: " + labSessionId));
            String vmName = "vm-" + session.getId();
            String namespace = session.getLab().getNamespace();

            session.setStatus(COMPLETED_STATUS);
            session.setExpiresAt(LocalDateTime.now());
            userLabSessionRepository.save(session);
            log.info("Session {} status updated to COMPLETED.", labSessionId);

            LabSessionCleanupRequest cleanupRequest = LabSessionCleanupRequest.builder()
                    .labSessionId(labSessionId)
                    .vmName(vmName)
                    .namespace(namespace)
                    .build();

            cleanupProducer.sendCleanupRequest(cleanupRequest);
            log.info("Sent cleanup request for session {} to infrastructure service.", labSessionId);

        } catch (Exception e) {
            throw new RuntimeException("Loi khi gui phien lab: " + e.getMessage(), e);
        }
    }

    public Optional<UserLabSession> findById(Integer labSessionId) {
        return userLabSessionRepository.findById(labSessionId);
    }

    public Optional<UserLabSession> checkActiveSession(Integer userId, Integer labId) {
        try {
            Optional<UserLabSession> sessionOpt = userLabSessionRepository.findNonCompletedSessionByUserAndLab(userId, labId, COMPLETED_STATUS);

            log.info(sessionOpt.isPresent() ?
                "User {} has active session {} for lab {}" :
                "User {} has no active session for lab {}",
                userId, sessionOpt.map(UserLabSession::getId).orElse(null), labId);
            return sessionOpt;
        } catch (Exception e) {
            throw new RuntimeException("Loi khi kiem tra phien lab dang hoat dong: " + e.getMessage(), e);
        }
    }

    public void deleteSession(Integer labSessionId) {
        try {
            log.info("Deleting session {}...", labSessionId);

            UserLabSession session = userLabSessionRepository.findById(labSessionId)
                .orElseThrow(() -> new EntityNotFoundException(
                    "Khong tim thay UserLabSession voi ID: " + labSessionId));

            if (COMPLETED_STATUS.equals(session.getStatus())) {
                throw new IllegalStateException("Khong the xoa phien lab da hoan thanh.");
            }

            userLabSessionRepository.delete(session);
            log.info("Session {} deleted successfully.", labSessionId);

        } catch (Exception e) {
            log.error("Error deleting session {}: {}", labSessionId, e.getMessage(), e);
            throw new RuntimeException("Loi khi xoa phien lab: " + e.getMessage(), e);
        }
    }

    public Page<LabSessionHistoryResponse> getListLabHistory(String keyword, Integer userId, Pageable pageable) {
        return userLabSessionRepository.findHistoryByUserId(keyword, userId, pageable)
                .map(session -> new LabSessionHistoryResponse(
                        session.getId(),
                        session.getLab().getTitle(),
                        session.getStatus(),
                        session.getSetupStartedAt(),
                        session.getSetupCompletedAt()
                ));
    }
}