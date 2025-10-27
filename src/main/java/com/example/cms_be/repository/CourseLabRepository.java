package com.example.cms_be.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cms_be.model.CourseLab;

@Repository
public interface CourseLabRepository  extends JpaRepository<CourseLab, Integer> {

    Optional<CourseLab> findByCourseId(Integer courseId);
    Optional<CourseLab> findByLabId(Integer labId);
}
