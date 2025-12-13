package com.example.cms_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cms_be.model.UserLabSession;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

import java.util.Optional;

@Repository
public interface UserLabSessionRepository extends JpaRepository<UserLabSession, Integer> {

    @Query("SELECT uls FROM UserLabSession uls " +
            "WHERE uls.courseUser.user.id = :userId " +
            "AND uls.lab.id = :labId " +
            "AND uls.status IN :statuses " +
            "ORDER BY uls.createdAt DESC")
    Optional<UserLabSession> findActiveSessionByUserAndLab(
            @Param("userId") Integer userId,
            @Param("labId") Integer labId,
            @Param("statuses") List<String> statuses
    );

    @Query("SELECT uls FROM UserLabSession uls " +
            "JOIN uls.courseUser cu " +
            "WHERE cu.user.id = :userId " +
            "ORDER BY uls.createdAt DESC")
    Page<UserLabSession> findHistoryByUserId(@Param("userId") Integer userId, Pageable pageable);
}
