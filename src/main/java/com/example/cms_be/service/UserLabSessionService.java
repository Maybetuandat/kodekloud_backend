package com.example.cms_be.service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.Optional;

import com.example.cms_be.dto.LabSessionHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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



    public Page<UserLabSession> getUserLabSessionPagination(Integer userId, String keyword, Pageable pageable) {
        return userLabSessionRepository.findByUserIdAndKeyword(userId, keyword, pageable);
    }


    public UserLabSession createAndStartSession(Integer labId, Integer userId) throws IOException {
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

            
            
            Optional<UserLabSession> existingSession = userLabSessionRepository.findNonCompletedSessionByUserAndLab(userId, labId, COMPLETED_STATUS);
            if (existingSession.isPresent()) {
                log.warn("User {} đã có session chưa hoàn thành cho lab {}. Từ chối tạo mới.", userId, labId);
                throw new IllegalStateException("Bạn đang có một phiên lab chưa hoàn thành. Vui lòng hoàn thành hoặc hủy phiên trước khi tạo mới.");
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

            orchestrationService.provisionAndSetupLabWithEagerLoading(savedSession);

            return savedSession;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo và khởi động phiên lab: " + e.getMessage(), e);
        }
    }
    public void submitSession(Integer labSessionId) {
        try {
            log.info("Submitting session {}...", labSessionId);

        UserLabSession session = userLabSessionRepository.findById(labSessionId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy UserLabSession với ID: " + labSessionId));

        session.setStatus("COMPLETED");
        session.setExpiresAt(LocalDateTime.now());
        userLabSessionRepository.save(session);
        log.info("Session {} status updated to COMPLETED.", labSessionId);


        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi gửi phiên lab: " + e.getMessage(), e);
        }
    }

    public Optional<UserLabSession> findById(Integer labSessionId) {
        return userLabSessionRepository.findById(labSessionId);
    }

    public Optional<UserLabSession> checkActiveSession(Integer userId, Integer labId) {

      try {
            Optional<UserLabSession> sessionOpt = userLabSessionRepository.findNonCompletedSessionByUserAndLab(userId, labId, COMPLETED_STATUS);

             log.info(  sessionOpt.isPresent() ?
            "User {} has active session {} for lab {}" :
            "User {} has no active session for lab {}",
            userId, sessionOpt.map(UserLabSession::getId).orElse(null), labId);
            return sessionOpt;
      } catch (Exception e) {
            throw new RuntimeException("Lỗi khi kiểm tra phiên lab đang hoạt động: " + e.getMessage(), e);
      }
    }



    public void deleteSession(Integer labSessionId) {
    try {
        log.info("Deleting session {}...", labSessionId);

        UserLabSession session = userLabSessionRepository.findById(labSessionId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Không tìm thấy UserLabSession với ID: " + labSessionId));

        final String COMPLETED_STATUS = "COMPLETED";
        if (COMPLETED_STATUS.equals(session.getStatus())) {
                throw new IllegalStateException("Không thể xóa phiên lab đã hoàn thành.");
            }

            userLabSessionRepository.delete(session);
            log.info("Session {} deleted successfully.", labSessionId);

        } catch (Exception e) {
            log.error("Error deleting session {}: {}", labSessionId, e.getMessage(), e);
            throw new RuntimeException("Lỗi khi xóa phiên lab: " + e.getMessage(), e);
        }
    }

    public Page<LabSessionHistoryResponse> getListLabHistory(Integer userId, Pageable pageable) {
        return userLabSessionRepository.findHistoryByUserId(userId, pageable)
                .map(session -> new LabSessionHistoryResponse(
                        session.getId(),
                        session.getLab().getTitle(),
                        session.getStatus(),
                        session.getSetupStartedAt(),
                        session.getSetupCompletedAt()
                ));
    }
}