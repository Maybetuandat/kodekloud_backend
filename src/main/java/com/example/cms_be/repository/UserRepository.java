package com.example.cms_be.repository;


import com.example.cms_be.model.Lab;
import com.example.cms_be.model.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
        @Query("SELECT u FROM User u WHERE " +
                "(:isActive IS NULL OR u.isActive = :isActive) AND " +
                "(:keyword IS NULL OR :keyword = '' OR " +
                "(LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')))) ")
        Page<User> findWithFilters(
                @Param("keyword") String keyword,
                @Param("isActive") Boolean isActive,
                Pageable pageable
        );


        
        @Query("""
        SELECT DISTINCT u FROM User u
        JOIN u.courseUsers cu
        WHERE cu.course.id = :courseId
        AND (:isActive IS NULL OR u.isActive = :isActive)
        AND (COALESCE(:keyword, '') = '' 
                OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
        Page<User> findUsersByCourseId(
        @Param("courseId") Integer courseId,
        @Param("keyword") String keyword,
        @Param("isActive") Boolean isActive,
        Pageable pageable);

        @Query("""
        SELECT u FROM User u
        WHERE NOT EXISTS (
                SELECT 1 FROM CourseUser cu 
                WHERE cu.user = u AND cu.course.id = :courseId
        )
        AND (:isActive IS NULL OR u.isActive = :isActive)
        AND (COALESCE(:keyword, '') = '' 
                OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
        Page<User> findUsersNotInCourseId(
        @Param("courseId") Integer courseId,
        @Param("keyword") String keyword,
        @Param("isActive") Boolean isActive,
        Pageable pageable);
}