package com.flowcare.backend.staff.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "staff_service_types")
data class StaffServiceType(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,

    @Column(name = "staff_id", nullable = false)
    val staffId: String,

    @Column(name = "service_type_id", nullable = false)
    val serviceTypeId: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
