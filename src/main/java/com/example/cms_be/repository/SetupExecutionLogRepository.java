package com.example.cms_be.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cms_be.model.SetupExecutionLog;

public interface SetupExecutionLogRepository extends JpaRepository<SetupExecutionLog, String> {


    List<SetupExecutionLog> findByLabSessionIdOrderByStepOrder(String labSessionId);
    
}
