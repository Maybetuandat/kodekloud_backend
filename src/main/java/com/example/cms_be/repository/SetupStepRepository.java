package com.example.cms_be.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cms_be.model.SetupStep;

@Repository
public interface SetupStepRepository  extends JpaRepository<SetupStep, Integer> {

    List<SetupStep> findByLabIdOrderByStepOrder(Integer labId);

}
