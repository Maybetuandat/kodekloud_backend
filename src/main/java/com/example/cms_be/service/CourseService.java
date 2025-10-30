package com.example.cms_be.service;



import com.example.cms_be.dto.CourseDetailResponse;
import com.example.cms_be.dto.LabInfo;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import com.example.cms_be.model.Category;
import com.example.cms_be.model.Course;
import com.example.cms_be.repository.CategoryRepository;
import com.example.cms_be.repository.CourseRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;

    public Page<Course> getAllCourses(Pageable pageable, Boolean isActive, String keyword, String slugCategory)
    {
        try {
            return courseRepository.findWithFilters(keyword, isActive, slugCategory, pageable);
        } catch (Exception e) {
            log.error("Error fetching courses with filters: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch courses", e);
        }
    }

   
    public Course createCourse(Course course) {

        try {
            return courseRepository.save(course);
        } catch (Exception e) {
            log.error("Error creating course: {}", e.getMessage());
            throw new RuntimeException("Failed to create course", e);
        }
        
    }

    public Course getCourseById(Integer id) {
       try {
         return courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + id));
       } catch (Exception e) {
           log.error("Error fetching course by id: {}", e.getMessage());
           throw new RuntimeException("Failed to fetch course", e);
       }
    }

    public CourseDetailResponse getCourseDetailById(Integer id) {
        Course course = courseRepository.findCourseWithLabsById(id)
                .orElseThrow(() -> new EntityNotFoundException("Course not found with id: " + id));
        return mapToDetailDto(course);
    }

    private CourseDetailResponse mapToDetailDto(Course course) {
        CourseDetailResponse dto = new CourseDetailResponse();
        dto.setId(course.getId());
        dto.setTitle(course.getTitle());
        dto.setDescription(course.getDescription());

        List<LabInfo> labDtos = course.getCourseLabs().stream()
                .map(courseLab -> {
                    var lab = courseLab.getLab();
                    return new LabInfo(lab.getId(), lab.getTitle());
                })
                .collect(Collectors.toList());
        dto.setLabs(labDtos);

        return dto;
    }

    public Course updateCourse(Integer id, Course updatedCourse) {
      try {
          var existingCourse = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + id));

        if (updatedCourse.getCategory() != null) {
            Category category = categoryRepository.findById(updatedCourse.getCategory().getId())
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + updatedCourse.getCategory().getId()));
            existingCourse.setCategory(category);
        }
        if(updatedCourse.getTitle() != null) {
            existingCourse.setTitle(updatedCourse.getTitle());
        }
        if(updatedCourse.getDescription() != null) {
            existingCourse.setDescription(updatedCourse.getDescription());
        }
        if(updatedCourse.getShortDescription() != null) {
            existingCourse.setShortDescription(updatedCourse.getShortDescription());
        }
        if(updatedCourse.getLevel() != null) {
            existingCourse.setLevel(updatedCourse.getLevel());
        }
        if(updatedCourse.getIsActive() != null) {
            existingCourse.setIsActive(updatedCourse.getIsActive());
        }

        return courseRepository.save(existingCourse);
      } catch (Exception e) {
        log.error("Error updating course: {}", e.getMessage());
        throw new RuntimeException("Failed to update course", e);
      }
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
