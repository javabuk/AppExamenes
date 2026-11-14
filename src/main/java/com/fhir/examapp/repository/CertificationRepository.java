package com.fhir.examapp.repository;

import com.fhir.examapp.domain.Certification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificationRepository extends JpaRepository<Certification, Long> {

    Optional<Certification> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Certification> findByActiveTrueOrderBySortOrderAsc();

    List<Certification> findAllByOrderBySortOrderAsc();
}
