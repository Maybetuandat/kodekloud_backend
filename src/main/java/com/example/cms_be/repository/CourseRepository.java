package com.example.cms_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.cms_be.model.Course;


public interface CourseRepository extends JpaRepository<Course, Integer> {
    @Query("SELECT l FROM Lab l WHERE " +
           "(:isActive IS NULL OR l.isActive = :isActive) AND " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(l.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Course> findWithFilters(
            @Param("keyword") String keyword,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );
}