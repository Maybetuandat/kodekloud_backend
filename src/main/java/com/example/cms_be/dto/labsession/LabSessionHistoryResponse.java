package com.example.cms_be.dto.labsession;

import java.time.LocalDateTime;

public record LabSessionHistoryResponse(
        Integer sessionId,
        String labTitle,
        String status,            // PENDING, RUNNING, COMPLETED...
        LocalDateTime startAt,    // setupStartedAt
        LocalDateTime completedAt // setupCompletedAt hoặc expiresAt
) {}