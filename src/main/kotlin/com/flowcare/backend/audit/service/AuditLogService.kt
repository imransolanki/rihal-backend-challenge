package com.flowcare.backend.audit.service

import com.flowcare.backend.audit.model.AuditLog
import com.flowcare.backend.audit.repository.AuditLogRepository
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.*

@Service
class AuditLogService(private val auditLogRepository: AuditLogRepository) {
    
    fun log(
        actorId: String,
        actorRole: String,
        actionType: String,
        entityType: String,
        entityId: String,
        metadata: Map<String, Any>? = null
    ) {
        val auditLog = AuditLog(
            id = "audit_${UUID.randomUUID()}",
            actorId = actorId,
            actorRole = actorRole,
            actionType = actionType,
            entityType = entityType,
            entityId = entityId,
            timestamp = OffsetDateTime.now(),
            metadata = metadata
        )
        auditLogRepository.save(auditLog)
    }
}
