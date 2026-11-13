package com.fhir.examapp.repository;

import com.fhir.examapp.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop100ByOrderByIdDesc();

    Page<AuditLog> findAllByOrderByIdDesc(Pageable pageable);

    List<AuditLog> findByActorUserIdOrderByIdDesc(Long actorUserId);
}
