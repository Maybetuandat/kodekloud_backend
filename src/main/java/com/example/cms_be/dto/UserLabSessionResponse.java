package com.example.cms_be.dto;

import java.time.LocalDateTime;

public record UserLabSessionResponse(
        String sessionId,
        String status,
        LocalDateTime startAt
) {}