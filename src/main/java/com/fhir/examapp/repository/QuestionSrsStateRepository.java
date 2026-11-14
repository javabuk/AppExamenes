package com.fhir.examapp.repository;

import com.fhir.examapp.domain.QuestionSrsState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionSrsStateRepository extends JpaRepository<QuestionSrsState, Long> {

    Optional<QuestionSrsState> findByUserIdAndQuestionId(Long userId, Long questionId);

    boolean existsByUserIdAndQuestionId(Long userId, Long questionId);

    List<QuestionSrsState> findByUserId(Long userId);

    @Query("SELECT s FROM QuestionSrsState s WHERE s.userId = :userId AND s.nextReviewDate <= :today ORDER BY s.nextReviewDate ASC")
    List<QuestionSrsState> findDueSrsStatesByUserId(@Param("userId") Long userId, @Param("today") String today);

    @Query("SELECT s FROM QuestionSrsState s WHERE s.userId = :userId AND s.certification.id = :certId AND s.nextReviewDate <= :today ORDER BY s.nextReviewDate ASC")
    List<QuestionSrsState> findDueSrsStatesByUserIdAndCertificationId(@Param("userId") Long userId, @Param("certId") Long certId, @Param("today") String today);

    @Query("SELECT COUNT(s) FROM QuestionSrsState s WHERE s.userId = :userId AND s.nextReviewDate <= :today")
    long countDueReviewsByUserId(@Param("userId") Long userId, @Param("today") String today);

    @Query("SELECT COUNT(s) FROM QuestionSrsState s WHERE s.userId = :userId AND s.certification.id = :certId AND s.nextReviewDate <= :today")
    long countDueReviewsByUserIdAndCertificationId(@Param("userId") Long userId, @Param("certId") Long certId, @Param("today") String today);

    @Query("SELECT COUNT(s) FROM QuestionSrsState s WHERE s.userId = :userId AND s.consecutiveCorrect >= 2")
    long countMasteredQuestionsByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(s) FROM QuestionSrsState s WHERE s.userId = :userId AND s.certification.id = :certId AND s.consecutiveCorrect >= 2")
    long countMasteredQuestionsByUserIdAndCertificationId(@Param("userId") Long userId, @Param("certId") Long certId);

    @Query("SELECT s FROM QuestionSrsState s WHERE s.nextReviewDate <= :today ORDER BY s.nextReviewDate ASC")
    List<QuestionSrsState> findDueSrsStates(@Param("today") String today);

    @Query("SELECT COUNT(s) FROM QuestionSrsState s WHERE s.nextReviewDate <= :today")
    long countDueReviews(@Param("today") String today);

    @Query("SELECT COUNT(s) FROM QuestionSrsState s WHERE s.consecutiveCorrect >= 2")
    long countMasteredQuestions();
}
