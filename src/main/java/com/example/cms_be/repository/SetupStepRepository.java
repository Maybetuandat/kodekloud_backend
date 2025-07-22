package com.example.cms_be.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cms_be.model.SetupStep;

public interface SetupStepRepository  extends JpaRepository<SetupStep, String> {

    List<SetupStep> findByLabIdOrderByStepOrder(String labId);

}
