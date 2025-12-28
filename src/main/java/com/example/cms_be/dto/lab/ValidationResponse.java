package com.example.cms_be.dto.lab;

import lombok.Builder;

@Builder
public record ValidationResponse(
    Integer labSessionId,
    Integer questionId,
    boolean isCorrect,
    String output,
    String error
) {}