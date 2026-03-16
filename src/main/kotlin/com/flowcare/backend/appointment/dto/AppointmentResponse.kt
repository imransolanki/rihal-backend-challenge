package com.flowcare.backend.appointment.dto

import com.flowcare.backend.appointment.model.AppointmentStatus
import java.time.OffsetDateTime

data class AppointmentResponse(
    val id: String,
    val customerId: String,
    val branchId: String,
    val serviceTypeId: String,
    val slotId: String?,
    val staffId: String?,
    val status: AppointmentStatus,
    val startAt: OffsetDateTime?,
    val endAt: OffsetDateTime?,
    val hasAttachment: Boolean,
    val createdAt: String
)
