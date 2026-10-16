package com.fhir.examapp.repository;

import com.fhir.examapp.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByTopicIdAndVerifiedTrue(Long topicId);

    List<Question> findByVerifiedTrue();

    List<Question> findByTopicId(Long topicId);

    @Query("SELECT q FROM Question q WHERE q.verified = true AND (LOWER(q.stem) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(q.explanation) LIKE LOWER(CONCAT('%', :kw, '%')))")
    List<Question> searchByKeyword(@Param("kw") String keyword);

    // Questions answered incorrectly at least once
    @Query("SELECT DISTINCT q FROM Question q JOIN ExamAttemptAnswer a ON a.question.id = q.id WHERE q.verified = true AND a.correct = false")
    List<Question> findPreviouslyFailedQuestions();

    // Questions that have never been answered in any attempt
    @Query("SELECT q FROM Question q WHERE q.verified = true AND q.id NOT IN (SELECT DISTINCT a.question.id FROM ExamAttemptAnswer a)")
    List<Question> findUnattemptedQuestions();

    // Error Inbox: Questions that have been answered incorrectly at least once AND whose last consecutive correct count in SRS is < 2
    @Query("SELECT DISTINCT q FROM Question q JOIN ExamAttemptAnswer a ON a.question.id = q.id " +
           "LEFT JOIN QuestionSrsState s ON s.questionId = q.id " +
           "WHERE q.verified = true AND a.correct = false AND (s.consecutiveCorrect IS NULL OR s.consecutiveCorrect < 2)")
    List<Question> findErrorInboxQuestions();
}
