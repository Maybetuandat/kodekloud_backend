package com.example.cms_be.dto;

import lombok.Data;
import java.util.List;

@Data
public class JwtResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Integer id;
    private String email;
    private String ten;
    private List<String> roles;

    public JwtResponse(String accessToken, String refreshToken, Integer id, String email, String ten, List<String> roles) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.id = id;
        this.email = email;
        this.ten = ten;
        this.roles = roles;
    }
}