package com.example.cms_be.repository;

import com.example.cms_be.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Integer> {

    Optional<Submission> findByUserLabSessionIdAndQuestionId(Integer userLabSessionId, Integer questionId);
}
