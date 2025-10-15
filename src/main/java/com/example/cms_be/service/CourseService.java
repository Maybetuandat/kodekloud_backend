package com.example.cms_be.service;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import com.example.cms_be.model.Course;
import com.example.cms_be.repository.CourseRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    
    public Page<Course> getAllCourses(Pageable pageable, Boolean isActive, String keyword)
    {
        return courseRepository.findWithFilters(keyword, isActive, pageable);
    }

    public Course createCourse(Course course) {

        return courseRepository.save(course);
    }

    public Course updateCourse(Integer id, Course updatedCourse) {
        var existingCourse = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + id));

        existingCourse.setTitle(updatedCourse.getTitle());
        existingCourse.setDescription(updatedCourse.getDescription());
        existingCourse.setLevel(updatedCourse.getLevel());
        existingCourse.setIsActive(updatedCourse.getIsActive());

        return courseRepository.save(existingCourse);
    }

    public Boolean deleteCourse(Integer id) {
        try {
             var existingCourse = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + id));
        courseRepository.delete(existingCourse);
        return true;
        } catch (Exception e) {
            log.error("Error deleting course: {}", e.getMessage());
            throw new RuntimeException("Failed to delete course", e);
            
        }
       
    }


    
}
