package com.example.cms_be.dto.lab;

import lombok.Builder;

@Builder
public record ValidationRequest(
    Integer labSessionId,
    Integer questionId,
    Integer userAnswerId,
    String vmName,
    String namespace,
    String podName,
    String validationCommand
) {}