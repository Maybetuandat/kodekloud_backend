package com.example.cms_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.cms_be.model.Course;

public interface CourseRepository extends JpaRepository<Course, Integer> {
    @Query("SELECT c FROM Course c " +
           "LEFT JOIN c.category cat " +
           "WHERE (:isActive IS NULL OR c.isActive = :isActive) " +
           "AND (:keyword IS NULL OR :keyword = '' OR LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:categorySlug IS NULL OR :categorySlug = '' OR cat.slug = :categorySlug)")
    Page<Course> findWithFilters(
            @Param("keyword") String keyword,
            @Param("isActive") Boolean isActive,
            @Param("categorySlug") String categorySlug,
            Pageable pageable
    );
}