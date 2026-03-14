package com.flowcare.backend.common.dto

data class AuthTestResponse(
    val message: String,
    val userId: String,
    val username: String,
    val role: String,
    val branchId: String? = null,
    val accessGranted: Boolean = true
)
