package com.flowcare.backend.service.dto

data class ServiceTypeResponse(
    val id: String,
    val name: String,
    val description: String?,
    val durationMinutes: Int
)
