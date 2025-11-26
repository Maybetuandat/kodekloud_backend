package com.example.cms_be.security.service;

import com.example.cms_be.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.*;

public class UserDetailsImpl implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private final Integer id;

    @Getter
    private final String email;

    private final String username;

    @JsonIgnore
    private final String password;

    // Thêm trường enabled để mapping với isActive của DB
    private final boolean enabled;

    private final Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Integer id, String email, String username, String password,
                           boolean enabled,
                           Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities;
    }

    public static UserDetailsImpl build(User user) {
        String roleName = "USER";
        if (user.getRole() != null) {
            roleName = user.getRole().getName();
        }
        if (!roleName.toUpperCase().startsWith("ROLE_")) {
            roleName = "ROLE_" + roleName.toUpperCase();
        }

        List<GrantedAuthority> authorities = new ArrayList<>();

        authorities.add(new SimpleGrantedAuthority(roleName));

        if (user.getRole() != null && user.getRole().getPermission() != null && !user.getRole().getPermission().isEmpty()) {
            String[] perms = user.getRole().getPermission().split(",");

            for (String perm : perms) {
                authorities.add(new SimpleGrantedAuthority(perm.trim().toUpperCase()));
            }
        }

        boolean isEnabled = user.getIsActive() != null ? user.getIsActive() : true;

        return new UserDetailsImpl(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getPassword(),
                isEnabled,
                authorities);
    }
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
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

    @Override
    public boolean isEnabled() {
        return enabled; // Trả về đúng trạng thái từ DB
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        UserDetailsImpl user = (UserDetailsImpl) o;
        return Objects.equals(id, user.id);
    }
}