package com.flowcare.backend.slot.dto

import java.time.LocalDateTime
import java.time.OffsetDateTime

data class SlotResponse(
    val id: String,
    val branchId: String,
    val serviceTypeId: String,
    val staffId: String?,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val capacity: Int,
    val bookedCount: Int,
    val isActive: Boolean,
    val deletedAt: OffsetDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
