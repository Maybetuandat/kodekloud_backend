package com.example.cms_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cms_be.model.UserLabSession;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

import java.util.Optional;

@Repository
public interface UserLabSessionRepository extends JpaRepository<UserLabSession, String> {

    @Query("SELECT uls FROM UserLabSession uls " +
            "WHERE uls.courseUser.user.id = :userId " +
            "AND uls.lab.id = :labId " +
            "AND uls.status IN :statuses " +
            "ORDER BY uls.createdAt DESC")
    Optional<UserLabSession> findActiveSessionByUserAndLab(
            @Param("userId") String userId,
            @Param("labId") String labId,
            @Param("statuses") List<String> statuses
    );
}
