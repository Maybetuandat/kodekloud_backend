package com.example.cms_be.dto;

import java.time.LocalDateTime;

public record LabSessionHistoryResponse(
        Integer sessionId,
        String labTitle,
        String status,
        LocalDateTime startAt,
        LocalDateTime completedAt
) {}