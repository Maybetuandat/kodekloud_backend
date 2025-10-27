package com.example.cms_be.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cms_be.model.Answer;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Integer> {

        List<Answer> findByQuestionId(Integer questionId);
} 
