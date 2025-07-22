package com.example.cms_be.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.cms_be.model.Lab;
import com.example.cms_be.model.SetupExecutionLog;
import com.example.cms_be.repository.LabRepository;
import com.example.cms_be.repository.SetupExecutionLogRepository;
import com.example.cms_be.repository.SetupStepRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabService {

    private final LabRepository labRepository;
    private final SetupStepRepository setupStepRepository;
    private final SetupExecutionLogRepository setupExecutionLogRepository;
    private final KubernetesService kubernetesService;



    public List<Lab> getAllLabs()
    {
        List<Lab> labs = new ArrayList<>();
        try {
             labs = labRepository.findAll();
        } catch (Exception e) {
            log.error("Error fetching labs: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch labs", e);
        }
        return labs;
    }

    public List<Lab> getLabsByActivateStatus(Boolean isActivate)
    {
        List<Lab> labs = new ArrayList<>();
        try {

            labs = labRepository.findLabsByActiveStatusOrderByCreatedAt(isActivate);
        }
        catch (Exception e) {
            log.error("Error fetching labs by activation status: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch labs by activation status", e);
        }
        return labs;
    }

}
