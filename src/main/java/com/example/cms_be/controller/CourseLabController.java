package com.example.cms_be.controller;

import org.springframework.web.bind.annotation.RestController;

import com.example.cms_be.dto.CreateCourseLabRequest;
import com.example.cms_be.model.CourseLab;
import com.example.cms_be.service.CourseLabService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;


@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/course-labs")
public class CourseLabController {

    private final CourseLabService courseLabService;

    @PostMapping("")
    public ResponseEntity<CourseLab> assignLabToCourse(@RequestBody CreateCourseLabRequest createCourseLabRequest) {
        CourseLab courseLab = courseLabService.assignLabToCourse(createCourseLabRequest);
        return ResponseEntity.ok(courseLab);
    }
    
}
