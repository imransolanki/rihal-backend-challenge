package com.flowcare.backend.common.dto

data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: String
)
