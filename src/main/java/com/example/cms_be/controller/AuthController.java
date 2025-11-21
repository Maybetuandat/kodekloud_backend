package com.example.cms_be.controller;


import com.example.cms_be.dto.*;
import com.example.cms_be.model.User;
import com.example.cms_be.repository.UserRepository;
import com.example.cms_be.security.jwt.JwtUtils;
import com.example.cms_be.security.service.UserDetailsImpl;
import com.example.cms_be.security.service.UserDetailsServiceImpl; // Cần import

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.util.List;
import java.util.stream.Collectors;
import com.nimbusds.jose.JOSEException;


@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String accessToken = jwtUtils.generateToken(authentication, false);
        String refreshToken = jwtUtils.generateToken(authentication, true);
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());
        return ResponseEntity.ok(new JwtResponse(
                accessToken,
                refreshToken,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                roles));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Lỗi: Email đã được sử dụng!"));
        }

        User user = new User();
        user.setEmail(signUpRequest.getEmail());
        user.setUsername(signUpRequest.getTen());

        user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));

        user.setRole("ROLE_USER");

        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Đăng ký người dùng thành công!"));
    }


    @PostMapping("/refreshtoken")
    public ResponseEntity<?> refreshtoken(@Valid @RequestBody RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        try {
            String username = jwtUtils.getUserNameFromToken(requestRefreshToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (jwtUtils.validateToken(requestRefreshToken, userDetails)) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                String newAccessToken = jwtUtils.generateToken(authentication, false);
                String newRefreshToken = jwtUtils.generateToken(authentication, true);

                return ResponseEntity.ok(new TokenRefreshResponse(newAccessToken, newRefreshToken));
            } else {
                return ResponseEntity.badRequest().body(new MessageResponse("Refresh token không hợp lệ!"));
            }
        } catch (ParseException | JOSEException e) {
            return ResponseEntity.badRequest().body(new MessageResponse("Lỗi: " + e.getMessage()));
        }
    }
}
