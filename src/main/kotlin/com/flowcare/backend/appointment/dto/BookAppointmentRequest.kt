package com.flowcare.backend.appointment.dto

import jakarta.validation.constraints.NotBlank

data class BookAppointmentRequest(
    @field:NotBlank(message = "Slot ID is required")
    val slotId: String
)
