package com.flowcare.backend.slot.dto

data class CleanupSummaryResponse(
    val slotsDeleted: Int,
    val retentionPeriodDays: Int,
    val cutoffDate: String
)
