package com.fhir.examapp.repository;

import com.fhir.examapp.domain.UserCertification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserCertificationRepository extends JpaRepository<UserCertification, Long> {

    List<UserCertification> findByUserIdAndActiveTrue(Long userId);

    List<UserCertification> findByUserId(Long userId);

    Optional<UserCertification> findByUserIdAndCertificationId(Long userId, Long certificationId);

    boolean existsByUserIdAndCertificationIdAndActiveTrue(Long userId, Long certificationId);

    @Query("SELECT uc.certification.id FROM UserCertification uc WHERE uc.user.id = :userId AND uc.active = true")
    List<Long> findActiveCertificationIdsByUserId(@Param("userId") Long userId);
}
