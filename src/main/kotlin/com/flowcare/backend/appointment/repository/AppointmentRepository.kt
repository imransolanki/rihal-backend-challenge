package com.flowcare.backend.appointment.repository

import com.flowcare.backend.appointment.model.Appointment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AppointmentRepository : JpaRepository<Appointment, String> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: String): List<Appointment>
}
