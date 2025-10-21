package com.example.cms_be.repository;

import com.example.cms_be.model.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Integer> {

    @Query("SELECT q FROM Question q " +
           "LEFT JOIN q.lab l " +
           "WHERE (:keyword IS NULL OR :keyword = '' OR LOWER(q.question) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:labId IS NULL OR l.id = :labId)")
    Page<Question> findWithFilters(
            @Param("keyword") String keyword,
            @Param("labId") Integer labId,
            Pageable pageable
    );
    
}
