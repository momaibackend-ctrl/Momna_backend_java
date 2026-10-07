package com.momna.platform.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    List<AuditEventEntity> findTop200ByTraceIdOrderByOccurredAtDesc(String traceId);
    List<AuditEventEntity> findTop200BySubjectRefOrderByOccurredAtDesc(String subjectRef);
}
