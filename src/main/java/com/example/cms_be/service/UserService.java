package com.example.cms_be.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.cms_be.dto.user.CreateUserRequest;
import com.example.cms_be.dto.user.UserDTO;
import com.example.cms_be.model.Role;
import com.example.cms_be.model.User;
import com.example.cms_be.repository.RoleRepository;
import com.example.cms_be.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public Page<UserDTO> getAllUsersWithPagination(Pageable pageable, Boolean isActive, String search) {
        try {
            Page<User> userPage = userRepository.findWithFilters(search, isActive, pageable);
            return userPage.map(this::convertToDTO);
        } catch (Exception e) {
            log.error("Error fetching users with pagination: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch users", e);
        }
    }

    public Page<UserDTO> getUsersByCourseId(Integer courseId, String search, Boolean isActive, Pageable pageable) {
        try {
            Page<User> usersInCourse = userRepository.findUsersByCourseId(courseId, search, isActive, pageable);
            return usersInCourse.map(this::convertToDTO);
        } catch (Exception e) {
            log.error("Error fetching users by course ID {}: {}", courseId, e.getMessage());
            throw new RuntimeException("Failed to fetch users by course ID", e);
        }
    }

    public Page<UserDTO> getUsersNotInCourse(Integer courseId, String roleName, String search, Boolean isActive, Pageable pageable) {
        try {
            Page<User> usersInCourse = userRepository.findUsersNotInCourseId(courseId, roleName, search, isActive, pageable);
            return usersInCourse.map(this::convertToDTO);
        } catch (Exception e) {
            log.error("Error fetching users by course ID {}: {}", courseId, e.getMessage());
            throw new RuntimeException("Failed to fetch users by course ID", e);
        }
    }

    public User createUser(CreateUserRequest request) {
        try {
            User user = new User();
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());
            user.setPhoneNumber(request.getPhoneNumber());
            user.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

            String encodedPassword = passwordEncoder.encode(request.getPassword());
            user.setPassword(encodedPassword);

            if (request.getRoleId() != null) {
                Role role = roleRepository.findById(request.getRoleId()).orElse(null);
                user.setRole(role);
            }

            return userRepository.save(user);
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage());
            throw new RuntimeException("Failed to create user", e);
        }
    }

    public UserDTO getUserById(Integer id) {
        try {
            User user = userRepository.findById(id).orElse(null);
            return user != null ? convertToDTO(user) : null;
        } catch (Exception e) {
            log.error("Error fetching user by ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to fetch user by ID", e);
        }
    }

    public User updateUser(Integer id, User userUpdate) {
        try {
            User existingUser = userRepository.findById(id).orElse(null);
            if (existingUser == null) {
                throw new RuntimeException("User not found with ID: " + id);
            }

            if (userUpdate.getFirstName() != null) {
                existingUser.setFirstName(userUpdate.getFirstName());
            }
            if (userUpdate.getLastName() != null) {
                existingUser.setLastName(userUpdate.getLastName());
            }
            if (userUpdate.getIsActive() != null) {
                existingUser.setIsActive(userUpdate.getIsActive());
            }
            if (userUpdate.getPassword() != null && !userUpdate.getPassword().isEmpty()) {
                existingUser.setPassword(passwordEncoder.encode(userUpdate.getPassword()));
            }
            if (userUpdate.getPhoneNumber() != null) {
                existingUser.setPhoneNumber(userUpdate.getPhoneNumber());
            }

            return userRepository.save(existingUser);
        } catch (Exception e) {
            log.error("Error updating user with ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to update user", e);
        }
    }

    public Boolean deleteUser(Integer id) {
        try {
            userRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            log.error("Error deleting user with ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    public UserDTO convertToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .lastName(user.getLastName())
                .firstName(user.getFirstName())
                .username(user.getUsername())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .isActive(user.getIsActive())
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .build();
    }
}