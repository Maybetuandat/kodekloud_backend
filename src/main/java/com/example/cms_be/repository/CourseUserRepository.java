package com.example.cms_be.repository;


import com.example.cms_be.model.Course;
import com.example.cms_be.model.CourseUser;
import com.example.cms_be.model.UserReplica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourseUserRepository extends JpaRepository<CourseUser, Integer> {

    @Query("""
        SELECT CASE WHEN COUNT(cu) > 0 THEN TRUE ELSE FALSE END
        FROM CourseUser cu
        WHERE cu.userReplica = :userReplica
          AND cu.course = :course
    """)
    boolean existsByUserReplicaAndCourse(
            @Param("userReplica") UserReplica userReplica,
            @Param("course") Course course
    );

    Optional<CourseUser> findByUserReplicaAndCourse(
            UserReplica userReplica,
            Course course
    );

    Optional<CourseUser> findByCourseIdAndUserReplicaId(
            Integer courseId,
            Integer userReplicaId
    );
}
