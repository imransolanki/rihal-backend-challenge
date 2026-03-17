package com.flowcare.backend.config.dto

data class RetentionConfigResponse(
    val retentionPeriodDays: Int,
    val description: String
)

data class UpdateRetentionRequest(
    val retentionPeriodDays: Int
)
