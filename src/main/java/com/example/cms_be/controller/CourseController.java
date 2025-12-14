package com.example.cms_be.controller;

import com.example.cms_be.dto.CourseDetailResponse;
import com.example.cms_be.dto.course.CreateCourseRequest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import com.example.cms_be.model.Course;
import com.example.cms_be.model.CourseLab;
import com.example.cms_be.model.CourseUser;
import com.example.cms_be.model.Lab;
import com.example.cms_be.model.User;
import com.example.cms_be.service.CourseLabService;
import com.example.cms_be.service.CourseService;
import com.example.cms_be.service.CourseUserService;
import com.example.cms_be.service.LabService;
import com.example.cms_be.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

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
    private final CourseLabService courseLabService;
    private final CourseUserService courseUserService;
    private final UserService userService;

    @GetMapping("")
    public ResponseEntity<?> getAllCourses(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String code
    ) {
        try {
            int pageNumber = page > 0 ? page - 1 : 0;

            System.err.println("Search parameter: '" + search + "'" + pageSize + " " + pageNumber + " " + isActive + " " + code);
            Pageable pageable = PageRequest.of(pageNumber, pageSize);

            if (search != null) {
                search = search.trim(); 
            }

            Page<Course> coursePage = courseService.getAllCourses(pageable, isActive, search, code);

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

    @GetMapping("/{courseId}/detail")
    public ResponseEntity<CourseDetailResponse> getCourseDetailById(@PathVariable Integer courseId) {
        CourseDetailResponse courseDetail = courseService.getCourseDetailById(courseId);
        return ResponseEntity.ok(courseDetail);
    }

    
    

    @GetMapping("/{courseId}/users")
    public ResponseEntity<?> getUsersByCourse(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @PathVariable Integer courseId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String search
    ) {
        try {
            if(page > 0){
                page = page - 1;
            }
            Pageable pageable = PageRequest.of(page, pageSize);
            Page<User> userPage = userService.getUsersByCourseId(courseId, search, isActive, pageable);
            Map<String, Object> response = new HashMap<>();
            response.put("data", userPage.getContent());
            response.put("currentPage", userPage.getNumber()  + 1);
            response.put("totalItems", userPage.getTotalElements());
            response.put("totalPages", userPage.getTotalPages());
            response.put("hasNext", userPage.hasNext());
            response.put("hasPrevious", userPage.hasPrevious());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting users by course id: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    


     @GetMapping("/{courseId}/users/not-in-course")
    public ResponseEntity<?> getUsersNotInCourse(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PathVariable Integer courseId
    ) {
        try {
            if(page > 0){
                page = page - 1;
            }
            Pageable pageable = PageRequest.of(page, pageSize);
            String roleName = "ROLE_STUDENT";
            Page<User> userPage = userService.getUsersNotInCourse(courseId, roleName, search, isActive, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("data", userPage.getContent());
            response.put("currentPage", userPage.getNumber() + 1);
            response.put("totalItems", userPage.getTotalElements());
            response.put("totalPages", userPage.getTotalPages());
            response.put("hasNext", userPage.hasNext());
            response.put("hasPrevious", userPage.hasPrevious());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting users not in course: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @GetMapping("/{courseId}/labs")
    public ResponseEntity<?> getLabsByCourse(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PathVariable Integer courseId
    ) {
        try {
            if(page > 0){
                page = page - 1;
            }
            Pageable pageable = PageRequest.of(page, pageSize);
            Page<Lab> labPage = labService.getLabsByCourseId(courseId, search, isActive, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("data", labPage.getContent());
            response.put("currentPage", labPage.getNumber() + 1);
            response.put("totalItems", labPage.getTotalElements());
            response.put("totalPages", labPage.getTotalPages());
            response.put("hasNext", labPage.hasNext());
            response.put("hasPrevious", labPage.hasPrevious());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting labs: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{courseId}/labs/not-in-course")
    public ResponseEntity<?> getLabsNotInCourse(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PathVariable Integer courseId
    ) {
        try {
            if(page > 0){
                page = page - 1;
            }
            Pageable pageable = PageRequest.of(page, pageSize);
            Page<Lab> labPage = labService.getLabsNotInCourse(courseId, search, isActive, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("data", labPage.getContent());
            response.put("currentPage", labPage.getNumber() + 1);
            response.put("totalItems", labPage.getTotalElements());
            response.put("totalPages", labPage.getTotalPages());
            response.put("hasNext", labPage.hasNext());
            response.put("hasPrevious", labPage.hasPrevious());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting labs: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @PostMapping("")
        public ResponseEntity<?> createCourse(@RequestBody CreateCourseRequest courseRequest) {
            log.info("Creating course: {}", courseRequest);
            try {
                Course createdCourse = courseService.createCourse(courseRequest);
                return ResponseEntity.status(HttpStatus.CREATED).body(createdCourse);
            } catch (Exception e) {
                log.error("Error creating course: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
    @PostMapping("/{courseId}/labs")
    public ResponseEntity<?> addLabsToCourseBulk(
            @PathVariable Integer courseId,
            @RequestBody List<Integer> labIds
    ) {
        try {
            List<CourseLab> courseLabs = courseLabService.assignLabsToCourseBulk(courseId, labIds);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(courseLabs);
        } catch (Exception e) {
            log.error("Error adding labs to course in bulk: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @PostMapping("/{courseId}/users")
    public ResponseEntity<List<CourseUser>> addUserToCourse(
            @PathVariable Integer courseId, 
            @RequestBody List<Integer> userIds)
    {
        try {
            List<CourseUser> enrollments = courseUserService.createListEnrollment(courseId, userIds);
            return ResponseEntity.status(HttpStatus.CREATED).body(enrollments);
            
        } catch (Exception e) {
            log.error("Error adding users to course: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }



    @PatchMapping("/{courseId}")
    public ResponseEntity<Course> updateCourse(
            @PathVariable Integer courseId,
            @RequestBody Course course
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

    @DeleteMapping("/{courseId}/labs/{labId}")
    public ResponseEntity<?> removeLabFromCourse(
            @PathVariable Integer courseId,
            @PathVariable Integer labId
    ) {
        try {
            courseLabService.removeLabFromCourse(courseId, labId);
            
            return ResponseEntity.ok(Map.of(
                "message", "Lab removed from course successfully",
                "courseId", courseId,
                "labId", labId
            ));
        } catch (Exception e) {
            log.error("Error removing lab from course: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    @DeleteMapping("/{courseId}/users/{userId}")
    public ResponseEntity<?> removeUserFromCourse(
            @PathVariable Integer courseId,
            @PathVariable Integer userId
    ) {
        try {
            courseUserService.removeUserFromCourse(courseId, userId);

            return ResponseEntity.ok(Map.of(
                "message", "User removed from course successfully",
                "courseId", courseId,
                "userId", userId
            ));
        } catch (Exception e) {
            log.error("Error removing user from course: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

}
