package com.example.cms_be.dto.labsession;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class LabSessionStatisticResponse {
    private Integer sessionId;
    private String labTitle;
    private String status;
    private int totalQuestions;
    private int correctAnswers;
    private List<SubmissionDetailDTO> submissions;
}

