package com.example.cms_be.repository;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.cms_be.model.Course;


import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Integer> {
    @Query("SELECT c FROM Course c " +
           "LEFT JOIN c.subject sub " +
           "WHERE (:isActive IS NULL OR c.isActive = :isActive) " +
           "AND (:keyword IS NULL OR :keyword = '' OR LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:code IS NULL OR :code = '' OR sub.code = :code)")
    Page<Course> findWithFilters(
            @Param("keyword") String keyword,
            @Param("isActive") Boolean isActive,
            @Param("code") String code,
            Pageable pageable
    );
    @Query("SELECT DISTINCT c FROM Course c " +
       "JOIN c.listCourseUser cu " +
       "LEFT JOIN c.subject sub " +
       "WHERE cu.user.id = :userId " +
       "AND (:isActive IS NULL OR c.isActive = :isActive) " +
       "AND (:keyword IS NULL OR :keyword = '' OR LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
       "AND (:code IS NULL OR :code = '' OR sub.code = :code)")
    Page<Course> findCoursesByUserId(
            @Param("userId") Integer userId,
            @Param("keyword") String keyword,
            @Param("isActive") Boolean isActive,
            @Param("code") String code,
            Pageable pageable
    );

    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.courseLabs cl LEFT JOIN FETCH cl.lab l WHERE c.id = :courseId")
    Optional<Course> findCourseWithLabsById(@Param("courseId") Integer courseId);
}