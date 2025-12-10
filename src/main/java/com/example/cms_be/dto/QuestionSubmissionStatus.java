package com.example.cms_be.dto;

public record QuestionSubmissionStatus(
        boolean isSubmitted,
        boolean isCorrect,
        Integer userAnswerId
) {}
