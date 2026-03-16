package com.flowcare.backend.slot.dto

import java.time.OffsetDateTime

data class AvailableSlotResponse(
    val id: String,
    val branchId: String,
    val serviceTypeId: String,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val capacity: Int,
    val availableCapacity: Int,
    val staff: StaffBasicInfo?
)
