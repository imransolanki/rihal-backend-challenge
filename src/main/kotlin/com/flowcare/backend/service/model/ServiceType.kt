package com.flowcare.backend.service.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "service_types")
data class ServiceType(
    @Id
    val id: String,

    @Column(name = "branch_id", nullable = false)
    val branchId: String,

    @Column(nullable = false)
    val name: String,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Column(name = "duration_minutes", nullable = false)
    val durationMinutes: Int,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
