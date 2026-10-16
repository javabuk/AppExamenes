package com.fhir.examapp.repository;

import com.fhir.examapp.domain.QuestionSrsState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionSrsStateRepository extends JpaRepository<QuestionSrsState, Long> {

    @Query("SELECT s FROM QuestionSrsState s WHERE s.nextReviewDate <= :today ORDER BY s.nextReviewDate ASC")
    List<QuestionSrsState> findDueSrsStates(@Param("today") String today);

    @Query("SELECT COUNT(s) FROM QuestionSrsState s WHERE s.nextReviewDate <= :today")
    long countDueReviews(@Param("today") String today);

    @Query("SELECT COUNT(s) FROM QuestionSrsState s WHERE s.consecutiveCorrect >= 2")
    long countMasteredQuestions();
}
