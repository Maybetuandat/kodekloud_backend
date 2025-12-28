package com.example.cms_be.dto.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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
