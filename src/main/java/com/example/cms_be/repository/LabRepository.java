package com.example.cms_be.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.cms_be.model.Lab;

public interface LabRepository extends JpaRepository<Lab, Integer> {
  
        @Query("SELECT l FROM Lab l WHERE " +
                "(:isActive IS NULL OR l.isActive = :isActive) AND " +
                "(:keyword IS NULL OR :keyword = '' OR " +
                "LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%')))")
        Page<Lab> findWithFilters(
                @Param("keyword") String keyword,
                @Param("isActive") Boolean isActive,
                Pageable pageable
        );

        @Query("""
        SELECT DISTINCT l FROM Lab l
        JOIN l.courseLabs cl
        WHERE cl.course.id = :courseId
        AND (:isActive IS NULL OR l.isActive = :isActive)
        AND (COALESCE(:keyword, '') = '' 
                OR LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
        Page<Lab> findLabsByCourseId(
        @Param("courseId") Integer courseId,
        @Param("keyword") String keyword,
        @Param("isActive") Boolean isActive,
        Pageable pageable);

        @Query("""
        SELECT l FROM Lab l
        WHERE NOT EXISTS (
                SELECT 1 FROM CourseLab cl 
                WHERE cl.lab = l AND cl.course.id = :courseId
        )
        AND (:isActive IS NULL OR l.isActive = :isActive)
        AND (COALESCE(:keyword, '') = '' 
                OR LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
        Page<Lab> findLabsNotInCourseId(
        @Param("courseId") Integer courseId,
        @Param("keyword") String keyword,
        @Param("isActive") Boolean isActive,
        Pageable pageable);

       
        @Query("SELECT l FROM Lab l LEFT JOIN FETCH l.setupSteps WHERE l.id = :id")
        Optional<Lab> findByIdWithSetupSteps(@Param("id") Integer id);
        @Query("SELECT l FROM Lab l " +
               "LEFT JOIN FETCH l.setupSteps " +
               "LEFT JOIN FETCH l.instanceType " +
               "WHERE l.id = :id")
        Optional<Lab> findByIdWithAllData(@Param("id") Integer id);
}