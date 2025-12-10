package com.example.cms_be.repository;

import com.example.cms_be.model.Course;
import com.example.cms_be.model.CourseUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourseUserRepository extends JpaRepository<CourseUser, Integer> {

    @Query("SELECT CASE WHEN COUNT(cu) > 0 THEN TRUE ELSE FALSE END " +
           "FROM CourseUser cu " +
           "WHERE cu.userId = :userId AND cu.course = :course")
    boolean existsByUserIdAndCourse(@Param("userId") Integer userId, @Param("course") Course course);
    @Query("SELECT cu FROM CourseUser cu WHERE cu.userId = :userId AND cu.course = :course")
    Optional<CourseUser> findByUserAndCourse(@Param("userId") Integer userId, 
                                             @Param("course") Course course);
    @Query("SELECT cu FROM CourseUser cu WHERE cu.course.id = :courseId AND cu.userId = :userId")
    Optional<CourseUser> findByCourseIdAndUserId(@Param("courseId") Integer courseId, 
                                                 @Param("userId") Integer userId);
}