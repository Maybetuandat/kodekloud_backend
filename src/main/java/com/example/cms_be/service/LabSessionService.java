package com.example.cms_be.service;


import com.example.cms_be.dto.kafka.LabProvisionRequest;
import com.example.cms_be.kafka.LabProvisionProducer;
import com.example.cms_be.model.*;
import com.example.cms_be.repository.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabSessionService {

    private final LabRepository labRepository;
    private final UserReplicaRepository userReplicaRepository;
    private final CourseUserRepository courseUserRepository;
    private final UserLabSessionRepository userLabSessionRepository;
    private final CourseLabRepository courseLabRepository;
    private final LabProvisionProducer labProvisionProducer;
    private final ObjectMapper objectMapper;

    @Transactional
    public UserLabSession createAndStartSession(Integer labId, Integer userId) throws Exception {
        Lab lab = labRepository.findById(labId)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy Lab với ID: " + labId));
   
        CourseLab courseLab = courseLabRepository.findByLabId(labId)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy CourseLab với Lab ID: " + labId));
        
        UserReplica user = userReplicaRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy User với ID: " + userId));

        boolean isEnrolled = courseUserRepository.existsByUserReplicaAndCourse(user, courseLab.getCourse());
        if (!isEnrolled) {
            throw new AccessDeniedException("Người dùng chưa đăng ký khóa học này.");
        }

        List<String> activeStatuses = List.of("PENDING", "RUNNING", "STARTING", "SETTING_UP");
        Optional<UserLabSession> existingSession = userLabSessionRepository.findActiveSessionByUserAndLab(userId, labId, activeStatuses);
        if (existingSession.isPresent()) {
            log.info("User {} already has an active session for lab {}", userId, labId);
            return existingSession.get();
        }

        UserLabSession session = new UserLabSession();
        session.setLab(lab);
        session.setSetupStartedAt(LocalDateTime.now());
        session.setStatus("PENDING");



        CourseUser courseUser = courseUserRepository.findByUserReplicaAndCourse(user, courseLab.getCourse())
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bản ghi đăng ký khóa học"));
        session.setCourseUser(courseUser);

        UserLabSession savedSession = userLabSessionRepository.save(session);
        log.info("Created UserLabSession {} for user {}", savedSession.getId(), userId);

        LabProvisionRequest provisionRequest = new LabProvisionRequest(
            savedSession.getId(),
            lab.getId(),
            userId,
            "vm-" + savedSession.getId(),
            lab.getNamespace(),
            lab.getInstanceType().getBackingImage(),
            lab.getInstanceType().getCpuCores(),
            lab.getInstanceType().getMemoryGb(),
            lab.getInstanceType().getStorageGb(),
            objectMapper.writeValueAsString(lab.getSetupSteps())
        );

        labProvisionProducer.sendProvisionRequest(provisionRequest);
        log.info("Sent provision request to infrastructure service for session {}", savedSession.getId());

        return savedSession;
    }

    @Transactional 
    public void submitSession(Integer labSessionId) {
        log.info("Submitting session {}...", labSessionId);

        UserLabSession session = userLabSessionRepository.findById(labSessionId)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy UserLabSession với ID: " + labSessionId));

        session.setStatus("COMPLETED");
        session.setExpiresAt(LocalDateTime.now());
        userLabSessionRepository.save(session);
        log.info("Session {} status updated to COMPLETED", labSessionId);

        LabProvisionRequest cleanupRequest = new LabProvisionRequest();
        cleanupRequest.setSessionId(session.getId());
        cleanupRequest.setVmName("vm-" + session.getId());
        cleanupRequest.setNamespace(session.getLab().getNamespace());
        
        labProvisionProducer.sendProvisionRequest(cleanupRequest);
    }

    public Optional<UserLabSession> findById(Integer labSessionId) {
        return userLabSessionRepository.findById(labSessionId);
    }
}
