
package com.example.cms_be.repository;

import com.example.cms_be.model.UserLabSession;

import org.springframework.data.domain.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserLabSessionRepository extends JpaRepository<UserLabSession, Integer> {
        
        
        
        @Query("SELECT uls FROM UserLabSession uls " +
           "WHERE uls.courseUser.course.id = :courseId " +
           "ORDER BY uls.createdAt DESC")
        List<UserLabSession> findByCourseId(@Param("courseId") Long courseId);




        @Query("SELECT uls FROM UserLabSession uls " +
                "WHERE uls.courseUser.user.id = :userId " +
                "AND uls.lab.id = :labId " +
                "AND uls.status <> :completedStatus " +
                "ORDER BY uls.createdAt DESC")
        Optional<UserLabSession> findNonCompletedSessionByUserAndLab(
                @Param("userId") Integer userId,
                @Param("labId") Integer labId,
                @Param("completedStatus") String completedStatus
        );
        @Query("SELECT uls FROM UserLabSession uls " +
           "JOIN FETCH uls.courseUser cu " +
           "JOIN FETCH cu.user " +
           "WHERE cu.course.id = :courseId " +
           "ORDER BY uls.createdAt DESC")
        List<UserLabSession> findByCourseIdWithUser(@Param("courseId") Integer courseId);
    
        @Query("SELECT COUNT(uls) FROM UserLabSession uls " +
                "WHERE uls.courseUser.user.id = :userId " +
                "AND uls.courseUser.course.id = :courseId " +
                "AND uls.status = 'COMPLETED'")
        int countCompletedByUserAndCourse(@Param("userId") Integer userId, @Param("courseId") Integer courseId);
    
        @Query("SELECT COUNT(uls) FROM UserLabSession uls " +
                "WHERE uls.courseUser.user.id = :userId " +
                "AND uls.courseUser.course.id = :courseId")
        int countTotalByUserAndCourse(@Param("userId") Integer userId, @Param("courseId") Integer courseId);
    
        @Query("SELECT MAX(uls.setupCompletedAt) FROM UserLabSession uls " +
                "WHERE uls.courseUser.user.id = :userId " +
                "AND uls.courseUser.course.id = :courseId")
        LocalDateTime findLastActivityByUserAndCourse(@Param("userId") Integer userId, @Param("courseId") Integer courseId);



        @Query("SELECT uls FROM UserLabSession uls " +
           "JOIN uls.courseUser cu " +
           "JOIN uls.lab l " +
           "WHERE cu.user.id = :userId " +
           "AND (:keyword IS NULL OR LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY uls.createdAt DESC")
        Page<UserLabSession> findByUserIdAndKeyword(
                @Param("userId") Integer userId, 
                @Param("keyword") String keyword, 
                Pageable pageable
        );
}