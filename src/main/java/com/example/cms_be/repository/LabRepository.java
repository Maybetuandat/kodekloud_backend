package com.example.cms_be.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.cms_be.model.Lab;

public interface LabRepository  extends JpaRepository<Lab, String> {
    
    @Query("SELECT l FROM Lab l WHERE l.isActive = :isActive ORDER BY l.createdAt DESC")
    List<Lab> findLabsByActiveStatusOrderByCreatedAt(Boolean isActive);
}
