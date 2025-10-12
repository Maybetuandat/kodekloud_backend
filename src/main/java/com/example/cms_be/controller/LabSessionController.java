package com.example.cms_be.controller;

import com.example.cms_be.dto.CreateLabSessionRequest;
import com.example.cms_be.dto.UserLabSessionResponse;
import com.example.cms_be.model.UserLabSession;
import com.example.cms_be.service.LabSessionService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.AccessDeniedException;
import java.util.Map;

@RestController
@RequestMapping("/api/lab-sessions")
@RequiredArgsConstructor
@Slf4j
public class LabSessionController {

    private final LabSessionService labSessionService;

    @PostMapping()
    public ResponseEntity<?> createLabSession(@Valid @RequestBody CreateLabSessionRequest request) {
        try {
            String userIdFromRequest = request.userId();
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
}