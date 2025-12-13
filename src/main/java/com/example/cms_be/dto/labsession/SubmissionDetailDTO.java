package com.example.cms_be.dto.labsession;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SubmissionDetailDTO {
    private Integer questionId;
    private String questionContent;
    private String userAnswerContent;
    private boolean isCorrect;
    private LocalDateTime submittedAt;
}
