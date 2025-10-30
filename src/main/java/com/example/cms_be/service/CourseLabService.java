package com.example.cms_be.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;


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

    

    public List<CourseLab> assignLabsToCourseBulk(Integer courseId, List<Integer> labIds) {
        try {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new IllegalArgumentException("Course not found with id: " + courseId));

            List<CourseLab> courseLabs = labIds.stream().map(labId -> {
                Lab lab = labRepository.findById(labId)
                        .orElseThrow(() -> new IllegalArgumentException("Lab not found with id: " + labId));

                return CourseLab.builder()
                        .course(course)
                        .lab(lab)
                        .build();
            }).toList();

            return courseLabRepository.saveAll(courseLabs);
        } catch (Exception e) {
            log.error("Error assigning labs to course in bulk: {}", e.getMessage());
            throw new RuntimeException("Failed to assign labs to course in bulk", e);
        }
    }
    public void removeLabFromCourse(Integer courseId, Integer labId) {
        try {
            Optional<CourseLab> courseLabOpt = courseLabRepository.findByCourseIdAndLabId(courseId, labId);
            if (courseLabOpt.isPresent()) {
                courseLabRepository.delete(courseLabOpt.get());
            } else {
                throw new IllegalArgumentException("No association found between course ID " + courseId + " and lab ID " + labId);
            }
        } catch (Exception e) {
            log.error("Error removing lab from course: {}", e.getMessage());
            throw new RuntimeException("Failed to remove lab from course", e);
        }
    }
}
