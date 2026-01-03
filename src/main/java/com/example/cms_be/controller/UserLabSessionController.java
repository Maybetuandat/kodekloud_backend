package com.example.cms_be.controller;
import java.nio.file.AccessDeniedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.cms_be.dto.LabSessionHistoryResponse;
import com.example.cms_be.dto.LabSessionStatisticResponse;
import com.example.cms_be.dto.SubmissionDetailDTO;
import com.example.cms_be.model.Submission;
import com.example.cms_be.service.SubmissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cms_be.dto.CreateLabSessionRequest;
import com.example.cms_be.dto.lab.UserLabSessionResponse;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.service.UserLabSessionService;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@RestController
@RequestMapping("/api/lab-sessions")
@RequiredArgsConstructor
@Slf4j
public class UserLabSessionController {

    
    private final UserLabSessionService userLabSessionService;
    private final SubmissionService submissionService;
    
    @Value("${infrastructure.service.websocket.student-url}")
    private String infrastructureWebSocketUrl;

    private final String COMPLETED_STATUS = "COMPLETED";


    // history for admin

    @GetMapping("/admin/history")
    public ResponseEntity<?> getHistorySession(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(value = "userId") Integer userId, 
            @RequestParam(value="keyword", required=false) String keyword
    ) {
        try {

            Integer currentUserId = userId;

            int pageNumber = page > 0 ? page - 1 : 0;
            Pageable pageable = PageRequest.of(pageNumber, pageSize);

            Page<LabSessionHistoryResponse> history = userLabSessionService.getListLabHistory(keyword,currentUserId, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("data", history.getContent());
            response.put("currentPage", history.getNumber() + 1);
            response.put("totalItems", history.getTotalElements());
            response.put("totalPages", history.getTotalPages());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching history: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Lỗi server"));
        }
    }




    // history for user

    @GetMapping("/history")
    public ResponseEntity<?> getListLabHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(value="keyword", required=false) String keyword,
            @RequestHeader(value = "X-User-Id") Integer userId
    ) {
        try {

            Integer currentUserId = userId;

            int pageNumber = page > 0 ? page - 1 : 0;
            Pageable pageable = PageRequest.of(pageNumber, pageSize);

            Page<LabSessionHistoryResponse> history = userLabSessionService.getListLabHistory(keyword,currentUserId, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("data", history.getContent());
            response.put("currentPage", history.getNumber() + 1);
            response.put("totalItems", history.getTotalElements());
            response.put("totalPages", history.getTotalPages());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching history: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Lỗi server"));
        }
    }

    @GetMapping("/{id}/statistic")
    public ResponseEntity<?>statisticLabSession(@PathVariable Integer id) {
        try {
            UserLabSession labSession = userLabSessionService.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy phiên Lab với ID: " + id));
            List<Submission> submissions = submissionService.findByUserLabSession(id);
            List<SubmissionDetailDTO> submissionDetails = submissions.stream().map(sub -> {
                String answerContent = "N/A";

                if (sub.getUserAnswer() != null) {
                    answerContent = sub.getUserAnswer().getContent();
                } else if (sub.getQuestion().getTypeQuestion() != null
                        && sub.getQuestion().getTypeQuestion().equals("check")) {
                    answerContent = "System Check (Auto)";
                }

                return SubmissionDetailDTO.builder()
                        .questionId(sub.getQuestion().getId())
                        .questionContent(sub.getQuestion().getQuestion())
                        .userAnswerContent(answerContent)
                        .isCorrect(sub.isCorrect())
                        .submittedAt(sub.getCreatedAt())
                        .build();
            }).toList();
            int totalCorrect = (int) submissions.stream().filter(Submission::isCorrect).count();
            LabSessionStatisticResponse response = LabSessionStatisticResponse.builder()
                    .sessionId(labSession.getId())
                    .labTitle(labSession.getLab().getTitle())
                    .status(labSession.getStatus())
                    .totalQuestions(submissions.size())
                    .correctAnswers(totalCorrect)
                    .submissions(submissionDetails)
                    .build();
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting statistic for session {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Lỗi server: " + e.getMessage()));
        }
    }








    @PostMapping()
    public ResponseEntity<?> createLabSession(@Valid @RequestBody CreateLabSessionRequest request) {
        try {
            Integer userIdFromRequest = request.userId();
            log.warn("!!! INSECURE !!! Using userId from request body: {}", userIdFromRequest);
            
            UserLabSession session = userLabSessionService.createAndStartSession(request.labId(), userIdFromRequest);
            
            String vmName = "vm-" + session.getId();
            String socketUrl = String.format("%s?podName=%s", infrastructureWebSocketUrl, vmName);
            
            log.info("Created lab session {} with socket URL: {}", session.getId(), socketUrl);
            
            UserLabSessionResponse responseDto = new UserLabSessionResponse(
                    session.getId(),
                    session.getStatus(),
                    session.getSetupStartedAt(),
                    socketUrl
            );

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(responseDto);

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create lab session for labId {}: {}", request.labId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Lỗi hệ thống."));
        }
    }


     @GetMapping("/{id}")
    public ResponseEntity<?> getLabSessionStatus(@PathVariable Integer id) {
        try {
            UserLabSession session = userLabSessionService.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy phiên Lab với ID: " + id));

            Map<String, Object> response = new HashMap<>();
            response.put("id", session.getId());
            response.put("status", session.getStatus());
            response.put("podName", session.getPodName());

            return ResponseEntity.ok(response);

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Lỗi server"));
        }
    }



    @GetMapping("/check-active/{labId}/{userId}")
    public ResponseEntity<?> checkActiveSession(
        @PathVariable Integer labId,
        @PathVariable Integer userId) {
    try {
        
        Optional<UserLabSession> activeSession = userLabSessionService.checkActiveSession(userId, labId);
            
        log.info(activeSession.isPresent() ?
            "User {} has active session {} for lab {}" :
            "User {} has no active session for lab {}",
            userId, activeSession.map(UserLabSession::getId).orElse(null), labId);
        if (activeSession.isPresent()) {
            UserLabSession session = activeSession.get();
            return ResponseEntity.ok(Map.of(
                "hasActiveSession", true,
                "sessionId", session.getId(),
                "status", session.getStatus(),
                "startedAt", session.getSetupStartedAt()
            ));
        }
        
        return ResponseEntity.ok(Map.of("hasActiveSession", false));
        
    } catch (Exception e) {
        log.error("Error checking active session for user {} lab {}: {}", 
            userId, labId, e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Lỗi hệ thống."));
    }
}

    @PostMapping("/{labSessionId}/submit")
    public ResponseEntity<?> handleSubmitSession(@PathVariable Integer labSessionId) {
        try {
            userLabSessionService.submitSession(labSessionId);

            return ResponseEntity.ok(Map.of(
                    "message", "Lab session " + labSessionId + " submitted successfully.",
                    "status", "COMPLETED"
            ));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to submit lab session {}: {}", labSessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Lỗi hệ thống."));
        }
    }



    @DeleteMapping("/{labSessionId}")
    public ResponseEntity<?> deleteLabSession(@PathVariable Integer labSessionId) {
        try {
        
            
            UserLabSession session = userLabSessionService.findById(labSessionId)
                .orElseThrow(() -> new EntityNotFoundException(
                    "Không tìm thấy phiên lab với ID: " + labSessionId));
            
            if (COMPLETED_STATUS.equals(session.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Không thể xóa phiên lab đã hoàn thành."));
            }
            
            userLabSessionService.deleteSession(labSessionId);
            
            log.info("Deleted lab session {} successfully", labSessionId);
            
            return ResponseEntity.ok(Map.of(
                "message", "Phiên lab đã được xóa thành công.",
                "sessionId", labSessionId
            ));
            
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete lab session {}: {}", labSessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Lỗi hệ thống."));
        }
    }

   
}