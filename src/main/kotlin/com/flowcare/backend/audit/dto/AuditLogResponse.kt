package com.flowcare.backend.audit.dto

import java.time.OffsetDateTime

data class AuditLogResponse(
    val id: String,
    val actorId: String,
    val actorRole: String,
    val actionType: String,
    val entityType: String,
    val entityId: String,
    val timestamp: OffsetDateTime,
    val metadata: Map<String, Any>?
)
