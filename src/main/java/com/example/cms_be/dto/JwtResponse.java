package com.example.cms_be.dto;

import lombok.Data;
import java.util.List;

@Data
public class JwtResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Integer id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private List<String> roles;

    public JwtResponse(String accessToken, String refreshToken, Integer id, String username, String email, 
                       String firstName, String lastName, List<String> roles) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.id = id;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.roles = roles;
    }
}