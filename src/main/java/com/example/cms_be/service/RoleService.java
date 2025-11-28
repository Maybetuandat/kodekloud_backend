package com.example.cms_be.service;

import com.example.cms_be.model.Role;
import com.example.cms_be.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;

    public Role getRoleByName(String name) {
        return this.roleRepository.findByName(name).orElse(null);
    }
}
