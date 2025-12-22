package com.example.cms_be.service;



import com.example.cms_be.dto.CourseDetailResponse;
import com.example.cms_be.dto.LabInfo;
import com.example.cms_be.dto.course.CreateCourseRequest;
import com.example.cms_be.dto.user.UserDTO;
import com.example.cms_be.dto.user.UserDTO;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import com.example.cms_be.model.Subject;
import com.example.cms_be.model.User;
import com.example.cms_be.model.Course;
import com.example.cms_be.repository.SubjectRepository;
import com.example.cms_be.repository.UserRepository;
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
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final CourseUserService courseUserService;
    public static final String STUDENT_ROLE = "ROLE_STUDENT";
    private static final String LECTURE_ROLE = "ROLE_LECTURER";
    private final UserService userService;

    private final Boolean DEFAULT_ACTIVE_COURSE_USER = true;

  

    public Page<Course> getCoursesByUser(Integer userId, Pageable pageable, Boolean isActive, String keyword, String code) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
            
            String roleName = user.getRole() != null ? user.getRole().getName() : "";
            
            if ("ROLE_ADMIN".equals(roleName) ) {
                return courseRepository.findWithFilters(keyword, isActive, code, pageable);
            } else {
                return courseRepository.findCoursesByUserId(userId, keyword, DEFAULT_ACTIVE_COURSE_USER, code, pageable);
            }
        } catch (Exception e) {
            log.error("Error fetching courses by user: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch courses", e);
        }
    }
    public Course createCourse(CreateCourseRequest courseRequest, Integer userId) {

        try {
            Subject subject = subjectRepository.findById(courseRequest.getSubjectId())
                    .orElseThrow(() -> new RuntimeException("Subject not found with id: " + courseRequest.getSubjectId()));
            Course course = new Course();
            course.setTitle(courseRequest.getTitle());
            course.setDescription(courseRequest.getDescription());
            course.setShortDescription(courseRequest.getShortDescription());
            course.setLevel(courseRequest.getLevel());
            course.setIsActive(courseRequest.getIsActive());
            course.setSubject(subject);    

            Course savedCourse = courseRepository.save(course);
              courseUserService.createEnrollment(savedCourse.getId(), userId);
            return savedCourse;
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
        

        Integer studentsCount = userRepository.countUsersByCourseIdAndRole(id, STUDENT_ROLE, true);

        
        List<User> lecturers = userRepository.findUsersByCourseIdAndRole(id, LECTURE_ROLE, true);

        UserDTO lecturerDTO = lecturers.isEmpty() ? null : userService.convertToDTO(lecturers.get(0));
        
        return mapToDetailDto(course, studentsCount, lecturerDTO);
    }

    private CourseDetailResponse mapToDetailDto(Course course, Integer studentsCount, UserDTO lecturer ) {
        CourseDetailResponse dto = new CourseDetailResponse();
        dto.setId(course.getId());
        dto.setTitle(course.getTitle());
        dto.setDescription(course.getDescription());
        dto.setShortDescription(course.getShortDescription());
        dto.setLevel(course.getLevel());
        
        dto.setStudentsCount(studentsCount != null ? studentsCount : 0);
        dto.setUpdatedAt(course.getUpdatedAt() != null ? course.getUpdatedAt().toString() : null);
        dto.setLecturer(lecturer);



        List<LabInfo> labDtos = course.getCourseLabs().stream()
                .map(courseLab -> {
                    var lab = courseLab.getLab();
                    return new LabInfo(lab.getId(), lab.getTitle(), lab.getDescription(), lab.getEstimatedTime(), lab.getInstanceType().getName());
                })
                .collect(Collectors.toList());
        dto.setLabs(labDtos);
        dto.setCategory(labDtos.get(0).category());

        return dto;
    }

    public Course updateCourse(Integer id, Course updatedCourse) {
      try {
          var existingCourse = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + id));

        if (updatedCourse.getSubject() != null) {
            Subject Subject = subjectRepository.findById(updatedCourse.getSubject().getId())
                    .orElseThrow(() -> new RuntimeException("Subject not found with id: " + updatedCourse.getSubject().getId()));
            existingCourse.setSubject(Subject);
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
