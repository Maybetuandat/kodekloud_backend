package com.example.cms_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cms_be.model.UserLabSession;

public interface UserLabSessionRepository extends JpaRepository<UserLabSession, String> {
    

}
