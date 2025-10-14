package com.example.cms_be.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLabSessionRequest(
        @NotBlank(message = "labId không được để trống")
        Integer labId,

        @NotBlank(message = "userId không được để trống")
        Integer userId
) {}