package com.fhir.examapp.repository;

import com.fhir.examapp.domain.SyllabusTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SyllabusTopicRepository extends JpaRepository<SyllabusTopic, Long> {
    Optional<SyllabusTopic> findByCode(String code);
}
