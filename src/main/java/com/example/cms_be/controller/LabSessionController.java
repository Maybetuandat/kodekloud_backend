package com.example.cms_be.controller;
import com.example.cms_be.dto.labsession.*;
import com.example.cms_be.model.Lab;
import com.example.cms_be.model.Submission;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.security.service.UserDetailsImpl;
import com.example.cms_be.service.LabService;
import com.example.cms_be.service.LabSessionService;
import com.example.cms_be.service.SubmissionService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.nio.file.AccessDeniedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@RestController
@RequestMapping("/api/lab-sessions")
@RequiredArgsConstructor
@Slf4j
public class LabSessionController {

    private final LabSessionService labSessionService;
    private final LabService labService;
    private final SubmissionService submissionService;

    @PreAuthorize("hasAuthority('LAB_SESSION_READ') or hasAuthority('LAB_SESSION_ALL')")
    @PostMapping()
    public ResponseEntity<?> createLabSession(@Valid @RequestBody CreateLabSessionRequest request) {
        try {
            Integer userIdFromRequest = request.userId();
            log.warn("!!! INSECURE !!! Using userId from request body: {}", userIdFromRequest);
            UserLabSession session = labSessionService.createAndStartSession(request.labId(), userIdFromRequest);
            UserLabSessionResponse responseDto = new UserLabSessionResponse(
                    session.getId(),
                    session.getStatus(),
                    session.getSetupStartedAt()
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

    @PostMapping("/{labSessionId}/submit")
    public ResponseEntity<?> handleSubmitSession(@PathVariable Integer labSessionId) {
        try {
            labSessionService.submitSession(labSessionId);

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


    @GetMapping("/{id}")
    public ResponseEntity<?> getLabSessionStatus(@PathVariable Integer id) {
        try {
            UserLabSession session = labSessionService.findById(id)
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

    @GetMapping("/history")
    public ResponseEntity<?> getListLabHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            Integer currentUserId = userDetails.getId();

            int pageNumber = page > 0 ? page - 1 : 0;
            Pageable pageable = PageRequest.of(pageNumber, pageSize);

            Page<LabSessionHistoryResponse> history = labSessionService.getListLabHistory(currentUserId, pageable);

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
            UserLabSession labSession = labSessionService.findById(id)
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
}