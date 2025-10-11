package com.example.cms_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.cms_be.model.Question;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Integer> {
}