package com.example.cms_be.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.cms_be.model.CourseUser;
import com.example.cms_be.repository.CourseRepository;
import com.example.cms_be.repository.CourseUserRepository;
import com.example.cms_be.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseUserService {
    
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseUserRepository courseUserRepository;


    public CourseUser createEnrollment(Integer courseId, Integer userId) {
       try {
         var course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + courseId));
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        var courseUser = new CourseUser();
        courseUser.setCourse(course);
        courseUser.setUser(user);

        return courseUserRepository.save(courseUser);
       } catch (Exception e) {
           throw new RuntimeException("Error creating enrollment: " + e.getMessage());
       }
    }
    public void removeEnrollment(Integer courseId, Integer userId) {
       try {
         var courseUser = courseUserRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found for course id: " + courseId + " and user id: " + userId));

        courseUserRepository.delete(courseUser);
       } catch (Exception e) {
           throw new RuntimeException("Error removing enrollment: " + e.getMessage());
       }
        
    }
    public List<CourseUser> createListEnrollment(Integer courseId, List<Integer> userIds) {
       try {
         var course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + courseId));

        List<CourseUser> enrollments = userIds.stream().map(userId -> {
            var user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

            var courseUser = new CourseUser();
            courseUser.setCourse(course);
            courseUser.setUser(user);

            return courseUser;
        }).toList();

        return courseUserRepository.saveAll(enrollments);
       } catch (Exception e) {
            throw new RuntimeException("Error creating list of enrollments: " + e.getMessage());
       }
    }
    @Transactional
    public void removeUserFromCourse(Integer courseId, Integer userId) {
       try {
         var courseUser = courseUserRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found for course id: " + courseId + " and user id: " + userId));

        courseUserRepository.delete(courseUser);
       } catch (Exception e) {
            log.error("Lỗi nghiêm trọng khi xóa sinh viên khỏi khóa học: ", e);
            throw new RuntimeException("Error removing user from course: " + (e.getMessage() != null ? e.getMessage() : "Null Pointer Exception"));
       }
    }
}
