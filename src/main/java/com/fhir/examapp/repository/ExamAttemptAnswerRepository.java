package com.fhir.examapp.repository;

import com.fhir.examapp.domain.ExamAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamAttemptAnswerRepository extends JpaRepository<ExamAttemptAnswer, Long> {

    List<ExamAttemptAnswer> findByAttemptId(Long attemptId);

    @Query("SELECT COUNT(a) FROM ExamAttemptAnswer a WHERE a.question.topic.id = :topicId")
    long countTotalByTopicId(@Param("topicId") Long topicId);

    @Query("SELECT COUNT(a) FROM ExamAttemptAnswer a WHERE a.question.topic.id = :topicId AND a.correct = true")
    long countCorrectByTopicId(@Param("topicId") Long topicId);

    @Query("SELECT AVG(a.timeSpentSeconds) FROM ExamAttemptAnswer a WHERE a.question.topic.id = :topicId AND a.timeSpentSeconds IS NOT NULL")
    Double averageTimeSpentByTopicId(@Param("topicId") Long topicId);

    @Query("SELECT AVG(a.timeSpentSeconds) FROM ExamAttemptAnswer a WHERE a.timeSpentSeconds IS NOT NULL")
    Double averageTimeSpentGlobal();

    @Query("SELECT COUNT(a) FROM ExamAttemptAnswer a WHERE a.flagged = true")
    long countFlaggedTotal();
}
