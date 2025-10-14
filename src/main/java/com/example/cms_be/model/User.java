package com.example.cms_be.model;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Data
@Table(name = "users")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, unique = true)
    private Integer id;

    @Column(name = "fullname")
    @NotBlank(message = "Tên user không được để trống")
    private String fullname;

    @Column(name = "username")
    @NotBlank(message = "Username không được để trống")
    private String username;

    @Column(name = "password")
    @NotBlank(message = "Password không được để trống")
    private String password;

    @Column(name = "email")
    @NotBlank(message = "Email không được để trống")
    private String email;

    @Column(name = "role")
    @NotBlank(message = "role cho user")
    private String role;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CourseUser> listCourseUser;

}
