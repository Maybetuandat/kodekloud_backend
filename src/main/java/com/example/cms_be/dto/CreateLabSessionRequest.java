package com.example.cms_be.dto;



public record CreateLabSessionRequest(
        Integer labId,
        Integer userId
) {}