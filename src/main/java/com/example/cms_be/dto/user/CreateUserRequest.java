package com.example.cms_be.dto.user;

import lombok.Data;

@Data
public class CreateUserRequest {

    private String lastName;
    private String firstName;
    private String username;
    private String password;
    private String email;
    private String phoneNumber;
    private Boolean isActive;
    private Integer roleId;
}
