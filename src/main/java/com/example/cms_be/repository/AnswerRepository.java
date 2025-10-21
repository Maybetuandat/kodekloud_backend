package com.example.cms_be.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cms_be.model.Answer;

public interface AnswerRepository extends JpaRepository<Answer, Integer> {

        List<Answer> findByQuestionId(Integer questionId);
} 
