// Repository
package com.example.cms_be.repository;

import com.example.cms_be.model.UserLabSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserLabSessionRepository extends JpaRepository<UserLabSession, Integer> {
        
        
        
      @Query("SELECT DISTINCT uls FROM UserLabSession uls " +
       "JOIN FETCH uls.courseUser cu " +
       "JOIN FETCH cu.user u " +
       "JOIN FETCH uls.lab l " +
       "LEFT JOIN FETCH l.labQuestions " +
       "LEFT JOIN FETCH uls.submissions " +
       "WHERE cu.course.id = :courseId")
        List<UserLabSession> findAllByCourseId(@Param("courseId") Integer courseId);




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
}