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

    @Query("SELECT q FROM Question q WHERE q.certification.id = :certId AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false)")
    List<Question> findByCertificationIdAndVerifiedTrue(@Param("certId") Long certId);

    @Query("SELECT q FROM Question q WHERE q.certification.id = :certId AND (q.area.id = :areaId OR q.topic.id = :areaId) AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false)")
    List<Question> findByCertificationIdAndAreaIdAndVerifiedTrue(@Param("certId") Long certId, @Param("areaId") Long areaId);

    @Query("SELECT DISTINCT q FROM Question q LEFT JOIN FETCH q.options LEFT JOIN FETCH q.area LEFT JOIN FETCH q.topic LEFT JOIN FETCH q.certification LEFT JOIN FETCH q.owner WHERE (q.deleted IS NULL OR q.deleted = false)")
    List<Question> findAllActive();

    @Query("SELECT DISTINCT q FROM Question q LEFT JOIN FETCH q.options LEFT JOIN FETCH q.area LEFT JOIN FETCH q.topic LEFT JOIN FETCH q.certification LEFT JOIN FETCH q.owner WHERE q.certification.id = :certId AND (q.deleted IS NULL OR q.deleted = false)")
    List<Question> findAllActiveByCertificationId(@Param("certId") Long certId);

    @Query("SELECT COUNT(q) FROM Question q WHERE q.verified = true AND (q.deleted IS NULL OR q.deleted = false)")
    long countActiveVerified();

    @Query("SELECT COUNT(q) FROM Question q WHERE q.certification.id = :certId AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false)")
    long countActiveVerifiedByCertificationId(@Param("certId") Long certId);

    @Query("SELECT q FROM Question q WHERE q.topic.id = :topicId AND (q.deleted IS NULL OR q.deleted = false)")
    List<Question> findByTopicId(@Param("topicId") Long topicId);

    @Query("SELECT q FROM Question q WHERE q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND (LOWER(q.stem) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(q.explanation) LIKE LOWER(CONCAT('%', :kw, '%')))")
    List<Question> searchByKeyword(@Param("kw") String keyword);

    @Query("SELECT DISTINCT q FROM Question q LEFT JOIN FETCH q.options LEFT JOIN FETCH q.area LEFT JOIN FETCH q.topic LEFT JOIN FETCH q.certification LEFT JOIN FETCH q.owner WHERE q.certification.id = :certId AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND (LOWER(q.stem) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(q.explanation) LIKE LOWER(CONCAT('%', :kw, '%')))")
    List<Question> searchByKeywordAndCertificationId(@Param("kw") String keyword, @Param("certId") Long certId);

    @Query("SELECT DISTINCT q FROM Question q LEFT JOIN FETCH q.options LEFT JOIN FETCH q.area LEFT JOIN FETCH q.topic LEFT JOIN FETCH q.certification LEFT JOIN FETCH q.owner WHERE q.certification.id = :certId AND (q.area.id = :topicOrAreaId OR q.topic.id = :topicOrAreaId) AND (q.deleted IS NULL OR q.deleted = false)")
    List<Question> findByCertificationIdAndAreaOrTopicId(@Param("certId") Long certId, @Param("topicOrAreaId") Long topicOrAreaId);

    // Questions answered incorrectly at least once (global fallback)
    @Query("SELECT DISTINCT q FROM Question q JOIN ExamAttemptAnswer a ON a.question.id = q.id WHERE q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND a.correct = false")
    List<Question> findPreviouslyFailedQuestions();

    @Query("SELECT DISTINCT q FROM Question q JOIN ExamAttemptAnswer a ON a.question.id = q.id WHERE a.attempt.user.id = :userId AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND a.correct = false")
    List<Question> findPreviouslyFailedQuestionsByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT q FROM Question q JOIN ExamAttemptAnswer a ON a.question.id = q.id WHERE a.attempt.user.id = :userId AND q.certification.id = :certId AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND a.correct = false")
    List<Question> findPreviouslyFailedQuestionsByUserIdAndCertificationId(@Param("userId") Long userId, @Param("certId") Long certId);

    // Questions that have never been answered in any attempt
    @Query("SELECT q FROM Question q WHERE q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND q.id NOT IN (SELECT DISTINCT a.question.id FROM ExamAttemptAnswer a)")
    List<Question> findUnattemptedQuestions();

    @Query("SELECT q FROM Question q WHERE q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND q.id NOT IN (SELECT DISTINCT a.question.id FROM ExamAttemptAnswer a WHERE a.attempt.user.id = :userId)")
    List<Question> findUnattemptedQuestionsByUserId(@Param("userId") Long userId);

    @Query("SELECT q FROM Question q WHERE q.certification.id = :certId AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND q.id NOT IN (SELECT DISTINCT a.question.id FROM ExamAttemptAnswer a WHERE a.attempt.user.id = :userId)")
    List<Question> findUnattemptedQuestionsByUserIdAndCertificationId(@Param("userId") Long userId, @Param("certId") Long certId);

    // Error Inbox: Questions that have been answered incorrectly at least once AND whose last consecutive correct count in SRS is < 2
    @Query("SELECT DISTINCT q FROM Question q JOIN ExamAttemptAnswer a ON a.question.id = q.id " +
           "LEFT JOIN QuestionSrsState s ON s.questionId = q.id " +
           "WHERE q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND a.correct = false AND (s.consecutiveCorrect IS NULL OR s.consecutiveCorrect < 2)")
    List<Question> findErrorInboxQuestions();

    @Query("SELECT DISTINCT q FROM Question q JOIN ExamAttemptAnswer a ON a.question.id = q.id " +
           "LEFT JOIN QuestionSrsState s ON s.questionId = q.id AND s.userId = :userId " +
           "WHERE a.attempt.user.id = :userId AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND a.correct = false AND (s.consecutiveCorrect IS NULL OR s.consecutiveCorrect < 2)")
    List<Question> findErrorInboxQuestionsByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT q FROM Question q JOIN ExamAttemptAnswer a ON a.question.id = q.id " +
           "LEFT JOIN QuestionSrsState s ON s.questionId = q.id AND s.userId = :userId " +
           "WHERE a.attempt.user.id = :userId AND q.certification.id = :certId AND q.verified = true AND (q.deleted IS NULL OR q.deleted = false) AND a.correct = false AND (s.consecutiveCorrect IS NULL OR s.consecutiveCorrect < 2)")
    List<Question> findErrorInboxQuestionsByUserIdAndCertificationId(@Param("userId") Long userId, @Param("certId") Long certId);
}
