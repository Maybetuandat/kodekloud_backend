package com.example.cms_be.dto.labsession;



public record CreateLabSessionRequest(
        Integer labId,
        Integer userId
) {}