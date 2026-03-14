package com.flowcare.backend.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RegistrationRequest(
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String = "",

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, message = "Password must be at least 6 characters")
    val password: String = "",

    @field:NotBlank(message = "Full name is required")
    @field:Size(max = 200, message = "Full name must not exceed 200 characters")
    val fullName: String = "",

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String = "",

    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone number must be valid")
    val phone: String = ""
)
