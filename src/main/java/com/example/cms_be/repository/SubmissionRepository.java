package com.example.cms_be.repository;

import com.example.cms_be.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Integer> {

       Optional<Submission> findByUserLabSessionIdAndQuestionId(Integer userLabSessionId, Integer questionId);
       @Query("SELECT COUNT(s) FROM Submission s " +
              "WHERE s.userLabSession.id = :sessionId " +
              "AND s.isCorrect = true")
       int countCorrectBySessionId(@Param("sessionId") Integer sessionId);
    
       @Query("SELECT COUNT(s) FROM Submission s " +
              "WHERE s.userLabSession.courseUser.user.id = :userId " +
              "AND s.userLabSession.courseUser.course.id = :courseId")
       int countTotalByUserAndCourse(@Param("userId") Integer userId, @Param("courseId") Integer courseId);
    
       @Query("SELECT COUNT(s) FROM Submission s " +
              "WHERE s.userLabSession.courseUser.user.id = :userId " +
              "AND s.userLabSession.courseUser.course.id = :courseId " +
              "AND s.isCorrect = true")
       int countCorrectByUserAndCourse(@Param("userId") Integer userId, @Param("courseId") Integer courseId);

       @Query("SELECT s FROM Submission s WHERE s.userLabSession.id = :labSessionId AND s.question.id = :questionId ORDER BY s.createdAt DESC")
       Optional<Submission> findLatestByLabSessionAndQuestion(
              @Param("labSessionId") Integer labSessionId, 
              @Param("questionId") Integer questionId
       );
    
       @Query("SELECT s FROM Submission s WHERE s.userLabSession.id = :labSessionId AND s.question.id = :questionId AND s.status = :status")
       Optional<Submission> findByLabSessionAndQuestionAndStatus(
              @Param("labSessionId") Integer labSessionId,
              @Param("questionId") Integer questionId,
              @Param("status") String status
       );
}
