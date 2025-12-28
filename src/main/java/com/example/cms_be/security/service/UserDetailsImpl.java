package com.example.cms_be.security.service;

import com.example.cms_be.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserDetailsImpl implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer id;
    private String email;
    private String firstName;
    private String lastName;
    private String username;
    
    @JsonIgnore
    private String password;
    
    private boolean enabled;
    private Collection<? extends GrantedAuthority> authorities;

    public static UserDetailsImpl build(User user) {
        String roleName = user.getRole() != null ? user.getRole().getName() : "USER";
        
        if (!roleName.toUpperCase().startsWith("ROLE_")) {
            roleName = "ROLE_" + roleName.toUpperCase();
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(roleName));

        if (user.getRole() != null && user.getRole().getPermission() != null && !user.getRole().getPermission().isEmpty()) {
            Arrays.stream(user.getRole().getPermission().split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .forEach(perm -> authorities.add(new SimpleGrantedAuthority(perm)));
        }

        return UserDetailsImpl.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .password(user.getPassword())
                .enabled(user.getIsActive() != null ? user.getIsActive() : true)
                .authorities(authorities)
                .build();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}