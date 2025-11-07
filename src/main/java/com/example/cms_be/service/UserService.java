package com.example.cms_be.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.cms_be.model.User;
import com.example.cms_be.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;


    public Page<User> getAllUsersWithPagination(Pageable pageable, Boolean isActive, String keyword)
    {
        try {
                    return userRepository.findWithFilters(keyword, isActive, pageable);
        } catch (Exception e) {
            log.error("Error fetching users with pagination: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch users", e);
        }
    }
    public Page<User> getUsersByCourseId(Integer courseId, String search, Boolean isActive, Pageable pageable) {
        try {
            Page<User> usersInCourse = userRepository.findUsersByCourseId(courseId, search, isActive, pageable);
            return usersInCourse;
        } catch (Exception e) {
            log.error("Error fetching users by course ID {}: {}", courseId, e.getMessage());
            throw new RuntimeException("Failed to fetch users by course ID", e);
        }
    }
      public Page<User> getUsersNotInCourse(Integer courseId,String search, Boolean isActive,  Pageable pageable) {
        try {
            Page<User> usersInCourse = userRepository.findUsersNotInCourseId(courseId, search, isActive, pageable);
            return usersInCourse;
        } catch (Exception e) {
            log.error("Error fetching users by course ID {}: {}", courseId, e.getMessage());
            throw new RuntimeException("Failed to fetch users by course ID", e);
        }
    }
    public User createUser(User user)
    {
        user.setIsActive(true);
        User createUser = new User();
        try{
            createUser = userRepository.save(user);
        }
        catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage());
            throw new RuntimeException("Failed to create user", e);
        }
        return createUser;
    }
    public User getUserById(Integer id)
    {
        try {
            return userRepository.findById(id).orElse(null);
        } catch (Exception e) {
            log.error("Error fetching user by ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to fetch user by ID", e);
        }
    }
    public User updateUser(Integer id, User userUpdate)
    {
        try {
            User existingUser = userRepository.findById(id).orElse(null);
            if (existingUser == null) {
                throw new RuntimeException("User not found with ID: " + id);
            }

            if(userUpdate.getFirstName() != null) {
                existingUser.setFirstName(userUpdate.getFirstName());
            }
            if(userUpdate.getLastName() != null) {
                existingUser.setLastName(userUpdate.getLastName());
            }
            if(userUpdate.getIsActive() != null) {
                existingUser.setIsActive(userUpdate.getIsActive());
            }
            if(userUpdate.getPassword() != null) {
                existingUser.setPassword(userUpdate.getPassword());
            }
            if(userUpdate.getPhoneNumber() != null) {
                existingUser.setPhoneNumber(userUpdate.getPhoneNumber());
            }
            

            return userRepository.save(existingUser);
        } catch (Exception e) {
            log.error("Error updating user with ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to update user", e);
        }
    }
    public Boolean deleteUser(Integer id)
    {
        try {
            userRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            log.error("Error deleting user with ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to delete user", e);
        }
    }
}
