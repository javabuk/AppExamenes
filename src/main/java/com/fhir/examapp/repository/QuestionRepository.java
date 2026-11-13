package com.fhir.examapp.repository;

import com.fhir.examapp.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    @Query("SELECT q FROM Question q WHERE q.topic.id = :topicId AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false)")
    List<Question> findByTopicIdAndVerifiedTrue(@Param("topicId") Long topicId);

    @Query("SELECT q FROM Question q WHERE q.material.id = :materialId AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false)")
    List<Question> findByMaterialIdAndVerifiedTrue(@Param("materialId") Long materialId);

    @Query("SELECT COUNT(q) FROM Question q WHERE q.material.id = :materialId AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false)")
    long countByMaterialIdAndVerifiedTrue(@Param("materialId") Long materialId);

    @Query("SELECT q FROM Question q WHERE q.verified = true AND (q.deleted IS NULL OR q.deleted = false)")
    List<Question> findByVerifiedTrue();

    @Query("SELECT q FROM Question q WHERE (q.deleted IS NULL OR q.deleted = false)")
    List<Question> findAllActive();

    @Query("SELECT COUNT(q) FROM Question q WHERE q.verified = true AND (q.deleted IS NULL OR q.deleted = false)")
    long countActiveVerified();

    @Query("SELECT q FROM Question q WHERE q.topic.id = :topicId AND (q.deleted IS NULL OR q.deleted = false)")
    List<Question> findByTopicId(@Param("topicId") Long topicId);

    @Query("SELECT q FROM Question q WHERE q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND (LOWER(q.stem) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(q.explanation) LIKE LOWER(CONCAT('%', :kw, '%')))")
    List<Question> searchByKeyword(@Param("kw") String keyword);

    // Questions answered incorrectly at least once (global fallback)
    @Query("SELECT DISTINCT q FROM Question q JOIN ExamAttemptAnswer a ON a.question.id = q.id WHERE q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND a.correct = false")
    List<Question> findPreviouslyFailedQuestions();

    @Query("SELECT DISTINCT q FROM Question q JOIN ExamAttemptAnswer a ON a.question.id = q.id WHERE a.attempt.user.id = :userId AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND a.correct = false")
    List<Question> findPreviouslyFailedQuestionsByUserId(@Param("userId") Long userId);

    // Questions that have never been answered in any attempt
    @Query("SELECT q FROM Question q WHERE q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND q.id NOT IN (SELECT DISTINCT a.question.id FROM ExamAttemptAnswer a)")
    List<Question> findUnattemptedQuestions();

    @Query("SELECT q FROM Question q WHERE q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND q.id NOT IN (SELECT DISTINCT a.question.id FROM ExamAttemptAnswer a WHERE a.attempt.user.id = :userId)")
    List<Question> findUnattemptedQuestionsByUserId(@Param("userId") Long userId);

    // Error Inbox: Questions that have been answered incorrectly at least once AND whose last consecutive correct count in SRS is < 2
    @Query("SELECT DISTINCT q FROM Question q JOIN ExamAttemptAnswer a ON a.question.id = q.id " +
           "LEFT JOIN QuestionSrsState s ON s.questionId = q.id " +
           "WHERE q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND a.correct = false AND (s.consecutiveCorrect IS NULL OR s.consecutiveCorrect < 2)")
    List<Question> findErrorInboxQuestions();

    @Query("SELECT DISTINCT q FROM Question q JOIN ExamAttemptAnswer a ON a.question.id = q.id " +
           "LEFT JOIN QuestionSrsState s ON s.questionId = q.id AND s.userId = :userId " +
           "WHERE a.attempt.user.id = :userId AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND a.correct = false AND (s.consecutiveCorrect IS NULL OR s.consecutiveCorrect < 2)")
    List<Question> findErrorInboxQuestionsByUserId(@Param("userId") Long userId);
}
