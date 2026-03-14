package com.flowcare.backend.auth.dto

data class RegistrationResponse(
    val id: String,
    val username: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val message: String = "Registration successful"
)
