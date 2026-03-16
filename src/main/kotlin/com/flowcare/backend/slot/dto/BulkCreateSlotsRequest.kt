package com.flowcare.backend.slot.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class BulkCreateSlotsRequest(
    @field:NotEmpty(message = "Slots list cannot be empty")
    @field:Size(max = 100, message = "Cannot create more than 100 slots at once")
    @field:Valid
    val slots: List<CreateSlotRequest>
)
