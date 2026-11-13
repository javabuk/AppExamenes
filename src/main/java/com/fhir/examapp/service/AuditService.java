package com.fhir.examapp.service;

import com.fhir.examapp.domain.AuditLog;
import com.fhir.examapp.domain.User;
import com.fhir.examapp.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog logAction(Long actorUserId, String actorUsername, String action,
                              String targetType, String targetId, String details, String ipAddress) {
        AuditLog log = new AuditLog(actorUserId, actorUsername, action, targetType, targetId, details, ipAddress);
        return auditLogRepository.save(log);
    }

    @Transactional
    public AuditLog logUserAction(User actor, String action, String targetType, String targetId, String details, String ipAddress) {
        Long actorId = actor != null ? actor.getId() : null;
        String actorName = actor != null ? actor.getUsername() : "SYSTEM";
        return logAction(actorId, actorName, action, targetType, targetId, details, ipAddress);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecentLogs() {
        return auditLogRepository.findTop100ByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getLogsPage(Pageable pageable) {
        return auditLogRepository.findAllByOrderByIdDesc(pageable);
    }
}
