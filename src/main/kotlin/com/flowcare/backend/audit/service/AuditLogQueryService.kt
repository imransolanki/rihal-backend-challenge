package com.flowcare.backend.audit.service

import com.flowcare.backend.audit.dto.AuditLogResponse
import com.flowcare.backend.audit.repository.AuditLogRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class AuditLogQueryService(
    private val auditLogRepository: AuditLogRepository
) {
    
    fun listAllLogs(
        startDate: LocalDate?,
        endDate: LocalDate?,
        pageable: Pageable
    ): Page<AuditLogResponse> {
        val logs = if (startDate != null && endDate != null) {
            val start = startDate.atStartOfDay().atOffset(OffsetDateTime.now().offset)
            val end = endDate.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().offset)
            auditLogRepository.findByTimestampBetween(start, end, pageable)
        } else {
            auditLogRepository.findAll(pageable)
        }
        
        return logs.map { log ->
            AuditLogResponse(
                id = log.id,
                actorId = log.actorId,
                actorRole = log.actorRole,
                actionType = log.actionType,
                entityType = log.entityType,
                entityId = log.entityId,
                timestamp = log.timestamp,
                metadata = log.metadata
            )
        }
    }
    
    fun listLogsByBranch(
        branchId: String,
        startDate: LocalDate?,
        endDate: LocalDate?,
        pageable: Pageable
    ): Page<AuditLogResponse> {
        val logs = if (startDate != null && endDate != null) {
            val start = startDate.atStartOfDay().atOffset(OffsetDateTime.now().offset)
            val end = endDate.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().offset)
            auditLogRepository.findByBranchIdAndTimestampBetween(branchId, start, end, pageable)
        } else {
            auditLogRepository.findByBranchId(branchId, pageable)
        }
        
        return logs.map { log ->
            AuditLogResponse(
                id = log.id,
                actorId = log.actorId,
                actorRole = log.actorRole,
                actionType = log.actionType,
                entityType = log.entityType,
                entityId = log.entityId,
                timestamp = log.timestamp,
                metadata = log.metadata
            )
        }
    }
}
