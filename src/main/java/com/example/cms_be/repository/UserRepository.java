package com.example.cms_be.repository;

import com.example.cms_be.model.User;
import com.example.cms_be.model.UserLabSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
}
