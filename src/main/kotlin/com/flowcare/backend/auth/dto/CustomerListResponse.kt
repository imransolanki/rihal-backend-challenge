package com.flowcare.backend.auth.dto

import java.time.LocalDateTime

data class CustomerListResponse(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val registeredAt: LocalDateTime
)

data class CustomerDetailResponse(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val idDocumentPath: String?,
    val registeredAt: LocalDateTime,
    val appointmentCount: Int
)
