package com.example.cms_be.dto.lab;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidationResponse {
    private Integer labSessionId;
    private Integer questionId;
    private boolean isCorrect;
    private String output;
    private String error;
}