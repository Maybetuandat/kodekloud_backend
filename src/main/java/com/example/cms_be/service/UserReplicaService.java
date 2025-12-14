package com.example.cms_be.service;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import com.example.cms_be.model.UserReplica;
import com.example.cms_be.repository.UserReplicaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserReplicaService {

    private final UserReplicaRepository userReplicaRepository;

      public Page<UserReplica> getUsersByCourseId(Integer courseId, String search, Boolean isActive, Pageable pageable) {
        try {
            Page<UserReplica> usersInCourse = userReplicaRepository.findUsersByCourseId(courseId, search, isActive, pageable);
            return usersInCourse;
        } catch (Exception e) {
            log.error("Error fetching users by course ID {}: {}", courseId, e.getMessage());
            throw new RuntimeException("Failed to fetch users by course ID", e);
        }
        }
        public Page<UserReplica> getStudentsNotInCourse(Integer courseId,String role, String search, Boolean isActive,  Pageable pageable) {
        try {
            
            Page<UserReplica> usersInCourse = userReplicaRepository.findUsersNotInCourseId(courseId, role,search, isActive, pageable);
            return usersInCourse;
        } catch (Exception e) {
            log.error("Error fetching users by course ID {}: {}", courseId, e.getMessage());
            throw new RuntimeException("Failed to fetch users by course ID", e);
        }
    }
    
}
