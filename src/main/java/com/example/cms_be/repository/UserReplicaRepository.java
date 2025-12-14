package com.example.cms_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.cms_be.model.UserReplica;

public interface UserReplicaRepository  extends JpaRepository<UserReplica, Integer> {
    


     @Query("""
        SELECT DISTINCT u FROM UserReplica u
        JOIN u.courseUsers cu
        WHERE cu.course.id = :courseId
        AND (:isActive IS NULL OR u.isActive = :isActive)
        AND (COALESCE(:search, '') = '' 
                OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
        Page<UserReplica> findUsersByCourseId(
        @Param("courseId") Integer courseId,
        @Param("search") String search,
        @Param("isActive") Boolean isActive,
        Pageable pageable);

        @Query("""
        SELECT u FROM UserReplica u
        WHERE NOT EXISTS (
                SELECT 1 FROM CourseUser cu
                WHERE cu.userReplica = u
                AND cu.course.id = :courseId
        )
        AND u.role = :role
        AND (:isActive IS NULL OR u.isActive = :isActive)
        AND (
                COALESCE(:search, '') = ''
                OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) 
                LIKE LOWER(CONCAT('%', :search, '%'))
        )
        """)
        Page<UserReplica> findUsersNotInCourseId(
        @Param("courseId") Integer courseId,
        @Param("role") String role,
        @Param("search") String search,
        @Param("isActive") Boolean isActive,
        Pageable pageable
        );



}
