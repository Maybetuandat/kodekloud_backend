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

    @Query("SELECT " +
           "u.id, " +
           "u.username, " +
           "CONCAT(u.lastName, ' ', u.firstName), " +
           "COUNT(CASE WHEN uls.status = 'COMPLETED' THEN 1 END), " +
           "(SELECT COUNT(cl.id) FROM CourseLab cl WHERE cl.course.id = :courseId), " +
           "AVG(TIMESTAMPDIFF(MINUTE, uls.setupStartedAt, uls.expiresAt)), " +
           "SUM(TIMESTAMPDIFF(MINUTE, uls.setupStartedAt, uls.expiresAt)), " +
           "MAX(uls.expiresAt) " +
           "FROM UserLabSession uls " +
           "JOIN uls.courseUser cu " +
           "JOIN cu.user u " +
           "WHERE cu.course.id = :courseId " +
           "GROUP BY u.id, u.username, u.lastName, u.firstName " +
           "ORDER BY COUNT(CASE WHEN uls.status = 'COMPLETED' THEN 1 END) DESC")
    List<Object[]> findLeaderboardByCourseId(@Param("courseId") Integer courseId);

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