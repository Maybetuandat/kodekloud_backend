package com.example.cms_be.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cms_be.dto.user.UserDTO;
import com.example.cms_be.model.User;
import com.example.cms_be.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
import org.springframework.web.bind.annotation.RequestParam;


@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping()
    public ResponseEntity<?> getAllUserWithPagination(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive
    ) {
        try {
            if(page > 0) {
                page = page - 1;
            }
            Pageable pageable = PageRequest.of(page, pageSize);
            Page<UserDTO> userPage = userService.getAllUsersWithPagination(pageable, isActive, search);
            Map<String, Object> response = Map.of(
                "data", userPage.getContent(),
                "currentPage", userPage.getNumber() + 1,
                "totalItems", userPage.getTotalElements(),
                "totalPages", userPage.getTotalPages(),
                "hasNext", userPage.hasNext(),
                "hasPrevious", userPage.hasPrevious()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
       
    }
    @GetMapping("/{userId}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Integer userId) {
        try {
            UserDTO userDTO = userService.getUserById(userId);
            return ResponseEntity.ok(userDTO);
        } catch (Exception e) {
            log.error("Error getting user by id: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    

    @PostMapping()
    public ResponseEntity<?> createUser(@RequestBody User user)
    {
        try {
            User createUser = userService.createUser(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(createUser);    
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        
    }
    @PatchMapping("/{userId}")
    public ResponseEntity<User> updateUser(
            @PathVariable Integer userId,
            @Valid @RequestBody User user
    ) {
        try {
            User updatedUser = userService.updateUser(userId, user);
            return ResponseEntity.ok(updatedUser);    
        } catch (Exception e) {
            log.error("Error updating user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        
    }
    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Integer userId) {
       try {
         boolean isDeleted = userService.deleteUser(userId);
        return ResponseEntity.ok(Map.of("message", "User with id " + userId + " has been deleted successfully."));
       } catch (Exception e) {
           log.error("Error deleting user: {}", e.getMessage());
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
       }
    }
}
