package com.example.cms_be.repository;

import java.util.List;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.cms_be.model.Lab;

public interface LabRepository  extends JpaRepository<Lab, String> {
    
    @Query("SELECT l FROM Lab l WHERE l.isActive = :isActive ORDER BY l.createdAt DESC")
    List<Lab> findLabsByActiveStatusOrderByCreatedAt(Boolean isActive);


    Page<Lab> findAll(Pageable pageable);

    Page<Lab> findByIsActive(Boolean isActive, Pageable pageable);



    @Query("SELECT l FROM Lab l WHERE " +
           "LOWER(l.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.baseImage) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Lab> searchLabs(@Param("search") String search, Pageable pageable);
    
    @Query("SELECT l FROM Lab l WHERE l.isActive = :isActive AND (" +
           "LOWER(l.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.baseImage) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Lab> searchLabsByActivateStatus(@Param("search") String search, 
                                       @Param("isActive") Boolean isActive, 
                                       Pageable pageable);
}
