package com.flowcare.backend.appointment.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "appointments")
data class Appointment(
    @Id
    val id: String,

    @Column(name = "customer_id", nullable = false)
    val customerId: String,

    @Column(name = "branch_id", nullable = false)
    val branchId: String,

    @Column(name = "service_type_id", nullable = false)
    val serviceTypeId: String,

    @Column(name = "slot_id")
    val slotId: String? = null,

    @Column(name = "staff_id")
    val staffId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: AppointmentStatus = AppointmentStatus.BOOKED,

    @Column(name = "attachment_path")
    val attachmentPath: String? = null,

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    var internalNotes: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
