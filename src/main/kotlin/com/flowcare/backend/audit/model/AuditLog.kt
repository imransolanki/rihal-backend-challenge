package com.flowcare.backend.audit.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Entity
@Table(name = "audit_logs")
data class AuditLog(
    @Id
    val id: String,

    @Column(name = "actor_id", nullable = false)
    val actorId: String,

    @Column(name = "actor_role", nullable = false)
    val actorRole: String,

    @Column(name = "action_type", nullable = false)
    val actionType: String,

    @Column(name = "entity_type", nullable = false)
    val entityType: String,

    @Column(name = "entity_id", nullable = false)
    val entityId: String,

    @Column(nullable = false)
    val timestamp: OffsetDateTime,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val metadata: Map<String, Any>? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
