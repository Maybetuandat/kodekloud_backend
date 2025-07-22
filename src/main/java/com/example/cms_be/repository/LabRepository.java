package com.example.cms_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cms_be.model.Lab;

public interface LabRepository  extends JpaRepository<Lab, String> {
    

}
