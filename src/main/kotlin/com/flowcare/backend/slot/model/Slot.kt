package com.flowcare.backend.slot.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Entity
@Table(name = "slots")
data class Slot(
    @Id
    val id: String,

    @Column(name = "branch_id", nullable = false)
    val branchId: String,

    @Column(name = "service_type_id", nullable = false)
    val serviceTypeId: String,

    @Column(name = "staff_id")
    val staffId: String? = null,

    @Column(name = "start_at", nullable = false)
    val startAt: OffsetDateTime,

    @Column(name = "end_at", nullable = false)
    val endAt: OffsetDateTime,

    @Column(nullable = false)
    val capacity: Int = 1,

    @Column(name = "booked_count", nullable = false)
    var bookedCount: Int = 0,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "deleted_at")
    var deletedAt: OffsetDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    var version: Long = 0
) {
    fun isAvailable(): Boolean = bookedCount < capacity && deletedAt == null && isActive
}
