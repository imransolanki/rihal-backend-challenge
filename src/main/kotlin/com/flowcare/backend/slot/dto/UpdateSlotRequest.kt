package com.flowcare.backend.slot.dto

import java.time.OffsetDateTime

data class UpdateSlotRequest(
    val startAt: OffsetDateTime? = null,
    val endAt: OffsetDateTime? = null,
    val staffId: String? = null,
    val removeStaffAssignment: Boolean = false,
    val capacity: Int? = null
)
