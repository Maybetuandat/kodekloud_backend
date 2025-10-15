package com.example.cms_be.service;

import org.springframework.stereotype.Service;

import com.example.cms_be.model.CourseUser;
import com.example.cms_be.repository.CourseRepository;
import com.example.cms_be.repository.CourseUserRepository;
import com.example.cms_be.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {
    
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseUserRepository courseUserRepository;


    public CourseUser createEnrollment(Integer courseId, Integer userId) {
        var course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + courseId));
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        var courseUser = new CourseUser();
        courseUser.setCourse(course);
        courseUser.setUser(user);

        return courseUserRepository.save(courseUser);
    }
}
