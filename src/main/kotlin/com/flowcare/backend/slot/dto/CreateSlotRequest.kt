package com.flowcare.backend.slot.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime

data class CreateSlotRequest(
    @field:NotBlank(message = "Branch ID is required")
    val branchId: String,
    @field:NotBlank(message = "Service type ID is required")
    val serviceTypeId: String,
    val staffId: String? = null,
    @field:NotNull(message = "Start time is required")
    val startAt: OffsetDateTime,
    @field:NotNull(message = "End time is required")
    val endAt: OffsetDateTime,
    @field:Min(value = 1, message = "Capacity must be at least 1")
    val capacity: Int = 1
)
