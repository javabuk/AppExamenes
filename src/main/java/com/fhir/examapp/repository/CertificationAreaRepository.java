package com.fhir.examapp.repository;

import com.fhir.examapp.domain.CertificationArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificationAreaRepository extends JpaRepository<CertificationArea, Long> {

    List<CertificationArea> findByCertificationIdAndActiveTrueOrderBySortOrderAsc(Long certificationId);

    List<CertificationArea> findByCertificationIdOrderBySortOrderAsc(Long certificationId);

    Optional<CertificationArea> findByCertificationIdAndCodeIgnoreCase(Long certificationId, String code);

    boolean existsByCertificationIdAndCodeIgnoreCase(Long certificationId, String code);
}
