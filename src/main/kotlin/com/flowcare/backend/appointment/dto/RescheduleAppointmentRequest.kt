package com.flowcare.backend.appointment.dto

import jakarta.validation.constraints.NotBlank

data class RescheduleAppointmentRequest(
    @field:NotBlank(message = "New slot ID is required")
    val newSlotId: String
)
