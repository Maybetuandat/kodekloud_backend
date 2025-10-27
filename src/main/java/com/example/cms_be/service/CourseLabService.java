package com.example.cms_be.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.cms_be.dto.CreateCourseLabRequest;
import com.example.cms_be.model.Course;
import com.example.cms_be.model.CourseLab;
import com.example.cms_be.model.Lab;
import com.example.cms_be.repository.CourseLabRepository;
import com.example.cms_be.repository.CourseRepository;
import com.example.cms_be.repository.LabRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CourseLabService {

    private final CourseLabRepository courseLabRepository;
    private final CourseRepository courseRepository;
    private final LabRepository labRepository;

    public CourseLab assignLabToCourse(CreateCourseLabRequest createCourseLabRequest) {
        try {
           Course course = courseRepository.findById(createCourseLabRequest.courseId())
                    .orElseThrow(() -> new IllegalArgumentException("Course not found with id: " + createCourseLabRequest.courseId()));
            Lab lab = labRepository.findById(createCourseLabRequest.labId())
                    .orElseThrow(() -> new IllegalArgumentException("Lab not found with id: " + createCourseLabRequest.labId()));

            CourseLab courseLab = CourseLab.builder()
                    .course(course)
                    .lab(lab)
                    .build();

            return courseLabRepository.save(courseLab);
        } catch (Exception e) {
            log.error("Error assigning lab to course: {}", e.getMessage());
            throw new RuntimeException("Failed to assign lab to course", e);
        }
    }

}
