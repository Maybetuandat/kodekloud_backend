package com.example.cms_be.dto;

import lombok.Builder;
import lombok.Data;
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
