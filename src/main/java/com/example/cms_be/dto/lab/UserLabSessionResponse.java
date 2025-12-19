package com.example.cms_be.dto.lab;

import java.time.LocalDateTime;

public record UserLabSessionResponse(
        Integer sessionId,
        String status,
        LocalDateTime startAt,
         String socketUrl
) {}