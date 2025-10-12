package com.example.cms_be.controller;

import com.example.cms_be.dto.CreateLabSessionRequest;
import com.example.cms_be.model.Lab;
import com.example.cms_be.service.LabService;
import com.example.cms_be.service.VMService;
import io.kubernetes.client.openapi.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/virtual-machine")
@RequiredArgsConstructor
public class VMController {
    private final VMService vmService;
    private final LabService labService;

}
