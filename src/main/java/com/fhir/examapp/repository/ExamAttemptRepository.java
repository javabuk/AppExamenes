package com.fhir.examapp.repository;

import com.fhir.examapp.domain.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {
    List<ExamAttempt> findTop10ByFinishedAtIsNotNullOrderByFinishedAtDesc();

    @Query("SELECT a FROM ExamAttempt a WHERE a.mode = 'SIMULATION' AND a.finishedAt IS NOT NULL ORDER BY a.finishedAt DESC")
    List<ExamAttempt> findRecentSimulationAttempts();
}
