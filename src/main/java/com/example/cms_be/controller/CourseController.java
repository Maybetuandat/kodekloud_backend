package com.example.cms_be.controller;

import org.springframework.web.bind.annotation.RestController;

import com.example.cms_be.model.Course;
import com.example.cms_be.model.Lab;
import com.example.cms_be.service.CourseService;
import com.example.cms_be.service.LabService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final LabService labService;
    
    @GetMapping("")
    public ResponseEntity<?> getAllCourses(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive
    ) {
        try {
            int pageNumber = page > 0 ? page - 1 : 0;

            System.err.println("Search parameter: '" + search + "'" + pageSize);
            Pageable pageable = PageRequest.of(pageNumber, pageSize);

            if (search != null) {
                search = search.trim(); 
            }
            Page<Course> coursePage = courseService.getAllCourses(pageable, isActive, search);

            Map<String, Object> response = new HashMap<>();
            response.put("data", coursePage.getContent());
            response.put("currentPage", coursePage.getNumber() + 1); 
            response.put("totalItems", coursePage.getTotalElements());
            response.put("totalPages", coursePage.getTotalPages());
            response.put("hasNext", coursePage.hasNext());
            response.put("hasPrevious", coursePage.hasPrevious());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting courses: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<?> getCourseById(@PathVariable Integer courseId) {
        try {
            Course course = courseService.getCourseById(courseId);
            if (course != null) {
                return ResponseEntity.ok(course);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Course not found");
            }
        } catch (Exception e) {
            log.error("Error getting course by id: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @PostMapping()
    public ResponseEntity<?> createCourse(@Valid @RequestBody Course course) {
        try {
            Course createdCourse = courseService.createCourse(course);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdCourse);
        } catch (Exception e) {
            log.error("Error creating course: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{courseId}/labs")
    public ResponseEntity<?> createLab(@RequestBody Lab lab, @PathVariable Integer courseId) {
        try {
            Lab createdLab = labService.createLab(lab, courseId);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdLab);
        } catch (Exception e) {
            log.error("Error creating lab: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    
    @PutMapping("/{courseId}")
    public ResponseEntity<Course> updateCourse(
            @PathVariable Integer courseId,
            @Valid @RequestBody Course course
    ) {
       try {
         Course updatedCourse = courseService.updateCourse(courseId, course);
        return ResponseEntity.ok(updatedCourse);
       } catch (Exception e) {
           log.error("Error updating course: {}", e.getMessage());
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
       }
    }

     @DeleteMapping("/{courseId}")
    public ResponseEntity<?> deleteCourse(@PathVariable Integer courseId) {
       try {
         boolean isDeleted = courseService.deleteCourse(courseId);
        return ResponseEntity.ok(Map.of("message", "Course with id " + courseId + " has been deleted successfully."));
       } catch (Exception e) {
           log.error("Error deleting course: {}", e.getMessage());
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
       }
    }


}
