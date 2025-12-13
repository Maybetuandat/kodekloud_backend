package com.example.cms_be.repository;

import com.example.cms_be.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Integer> {

    Optional<Submission> findByUserLabSessionIdAndQuestionId(Integer userLabSessionId, Integer questionId);

    @Query("SELECT s FROM Submission s " +
            "JOIN FETCH s.question q " +
            "LEFT JOIN FETCH s.userAnswer a " +
            "WHERE s.userLabSession.id = :userLabSessionId")
    List<Submission> findAllByUserLabSessionId(Integer userLabSessionId);
}
