package com.flowcare.backend.slot.dto

data class BulkCreateSlotsResponse(
    val successCount: Int,
    val failureCount: Int,
    val createdSlots: List<SlotResponse>,
    val failures: List<SlotCreationFailure>
)

data class SlotCreationFailure(
    val index: Int,
    val request: CreateSlotRequest,
    val reason: String
)
