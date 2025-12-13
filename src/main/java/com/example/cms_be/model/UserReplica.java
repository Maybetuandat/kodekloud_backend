package com.example.cms_be.model;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "user_replica")
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class UserReplica {

    @Id
    @Column(name = "id", nullable = false, unique = true)
    private Integer id;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "username", unique = true)
    private String username;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;


    @JsonIgnore
    @OneToMany(mappedBy = "userReplica", fetch = FetchType.LAZY)
    private Set<CourseUser> courseUsers;


   
}