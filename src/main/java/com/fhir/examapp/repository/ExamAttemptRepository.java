package com.fhir.examapp.repository;

import com.fhir.examapp.domain.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    List<ExamAttempt> findTop10ByFinishedAtIsNotNullOrderByFinishedAtDesc();

    @Query("SELECT a FROM ExamAttempt a WHERE a.user.id = :userId AND a.finishedAt IS NOT NULL ORDER BY a.finishedAt DESC")
    List<ExamAttempt> findFinishedByUserIdOrderByFinishedAtDesc(@Param("userId") Long userId);

    List<ExamAttempt> findTop10ByUser_IdAndFinishedAtIsNotNullOrderByFinishedAtDesc(Long userId);

    @Query("SELECT a FROM ExamAttempt a WHERE a.mode = 'SIMULATION' AND a.finishedAt IS NOT NULL ORDER BY a.finishedAt DESC")
    List<ExamAttempt> findRecentSimulationAttempts();

    @Query("SELECT a FROM ExamAttempt a WHERE a.user.id = :userId AND a.mode = 'SIMULATION' AND a.finishedAt IS NOT NULL ORDER BY a.finishedAt DESC")
    List<ExamAttempt> findRecentSimulationAttemptsByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT a FROM ExamAttempt a LEFT JOIN a.answers ans WHERE (a.material.id = :materialId OR ans.question.material.id = :materialId) AND a.finishedAt IS NOT NULL ORDER BY a.startedAt ASC")
    List<ExamAttempt> findFinishedAttemptsForMaterial(@Param("materialId") Long materialId);

    @Query("SELECT DISTINCT a FROM ExamAttempt a LEFT JOIN a.answers ans WHERE a.user.id = :userId AND (a.material.id = :materialId OR ans.question.material.id = :materialId) AND a.finishedAt IS NOT NULL ORDER BY a.startedAt ASC")
    List<ExamAttempt> findFinishedAttemptsForMaterialAndUser(@Param("materialId") Long materialId, @Param("userId") Long userId);

    @Query("SELECT a FROM ExamAttempt a WHERE a.finishedAt IS NOT NULL ORDER BY a.finishedAt DESC")
    List<ExamAttempt> findAllFinishedOrderByFinishedAtDesc();
}
