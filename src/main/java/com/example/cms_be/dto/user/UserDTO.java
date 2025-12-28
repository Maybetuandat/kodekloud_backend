package com.example.cms_be.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Integer id;
    private String lastName;
    private String firstName;
    private String username;
    private String email;
    private String phoneNumber;
    private Boolean isActive;
    private String roleName;
}