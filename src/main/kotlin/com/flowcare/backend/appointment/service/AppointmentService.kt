package com.flowcare.backend.appointment.service

import com.flowcare.backend.appointment.dto.AppointmentResponse
import com.flowcare.backend.appointment.exception.AppointmentNotFoundException
import com.flowcare.backend.appointment.exception.SlotNotAvailableException
import com.flowcare.backend.appointment.model.Appointment
import com.flowcare.backend.appointment.repository.AppointmentRepository
import com.flowcare.backend.audit.service.AuditLogService
import com.flowcare.backend.slot.repository.SlotRepository
import com.flowcare.backend.storage.service.FileStorageService
import jakarta.transaction.Transactional
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.*

@Service
class AppointmentService(
    private val appointmentRepository: AppointmentRepository,
    private val slotRepository: SlotRepository,
    private val fileStorageService: FileStorageService,
    private val auditLogService: AuditLogService
) {
    
    @Transactional
    fun bookAppointment(
        customerId: String,
        slotId: String,
        attachment: MultipartFile?,
        userRole: String
    ): AppointmentResponse {
        val slot = slotRepository.findById(slotId)
            .orElseThrow { SlotNotAvailableException("Slot not found") }
        
        if (!slot.isAvailable()) {
            throw SlotNotAvailableException("Slot is not available")
        }
        
        val appointmentId = "appt_${UUID.randomUUID()}"
        
        val attachmentPath = attachment?.let {
            fileStorageService.storeAppointmentAttachment(it, appointmentId)
        }
        
        val appointment = Appointment(
            id = appointmentId,
            customerId = customerId,
            branchId = slot.branchId,
            serviceTypeId = slot.serviceTypeId,
            slotId = slot.id,
            staffId = slot.staffId,
            attachmentPath = attachmentPath
        )
        
        slot.bookedCount++
        slot.updatedAt = LocalDateTime.now()
        
        try {
            slotRepository.save(slot)
            val saved = appointmentRepository.save(appointment)
            
            auditLogService.log(
                actorId = customerId,
                actorRole = userRole,
                actionType = "APPOINTMENT_CREATED",
                entityType = "Appointment",
                entityId = saved.id,
                metadata = mapOf(
                    "branchId" to slot.branchId,
                    "serviceTypeId" to slot.serviceTypeId,
                    "slotId" to slot.id,
                    "hasAttachment" to (attachmentPath != null)
                )
            )
            
            return toResponse(saved, slot)
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw SlotNotAvailableException("Slot was just booked by another customer")
        }
    }

    fun getCustomerAppointments(customerId: String): List<AppointmentResponse> {
        val appointments = appointmentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
        val slotIds = appointments.mapNotNull { it.slotId }
        val slots = slotRepository.findAllById(slotIds).associateBy { it.id }
        
        return appointments.map { appointment ->
            toResponse(appointment, slots[appointment.slotId])
        }
    }

    fun getAppointmentDetails(appointmentId: String, customerId: String): AppointmentResponse {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { AppointmentNotFoundException("Appointment not found") }
        
        if (appointment.customerId != customerId) {
            throw AccessDeniedException("Access denied")
        }
        
        val slot = appointment.slotId?.let { slotRepository.findById(it).orElse(null) }
        return toResponse(appointment, slot)
    }

    fun getAttachment(appointmentId: String, customerId: String): Path {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { AppointmentNotFoundException("Appointment not found") }
        
        if (appointment.customerId != customerId) {
            throw AccessDeniedException("Access denied")
        }
        
        val attachmentPath = appointment.attachmentPath
            ?: throw AppointmentNotFoundException("No attachment found")
        
        return fileStorageService.loadFile(attachmentPath)
    }

    @Transactional
    fun cancelAppointment(appointmentId: String, customerId: String, userRole: String): AppointmentResponse {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { AppointmentNotFoundException("Appointment not found") }
        
        if (appointment.customerId != customerId) {
            throw AccessDeniedException("Access denied")
        }
        
        if (appointment.status == com.flowcare.backend.appointment.model.AppointmentStatus.CANCELLED) {
            throw com.flowcare.backend.appointment.exception.InvalidAppointmentStateException("Appointment is already cancelled")
        }
        
        if (appointment.status == com.flowcare.backend.appointment.model.AppointmentStatus.COMPLETED) {
            throw com.flowcare.backend.appointment.exception.InvalidAppointmentStateException("Cannot cancel completed appointment")
        }
        
        val updatedAppointment = appointment.copy(
            status = com.flowcare.backend.appointment.model.AppointmentStatus.CANCELLED,
            updatedAt = LocalDateTime.now()
        )
        
        appointment.slotId?.let { slotId ->
            slotRepository.findById(slotId).ifPresent { slot ->
                slot.bookedCount = maxOf(0, slot.bookedCount - 1)
                slot.updatedAt = LocalDateTime.now()
                slotRepository.save(slot)
            }
        }
        
        val saved = appointmentRepository.save(updatedAppointment)
        
        auditLogService.log(
            actorId = customerId,
            actorRole = userRole,
            actionType = "APPOINTMENT_CANCELLED",
            entityType = "Appointment",
            entityId = saved.id,
            metadata = mapOf(
                "branchId" to saved.branchId,
                "serviceTypeId" to saved.serviceTypeId,
                "slotId" to (saved.slotId ?: ""),
                "cancelledAt" to LocalDateTime.now().toString()
            )
        )
        
        val slot = saved.slotId?.let { slotRepository.findById(it).orElse(null) }
        return toResponse(saved, slot)
    }

    @Transactional
    fun rescheduleAppointment(
        appointmentId: String,
        customerId: String,
        newSlotId: String,
        userRole: String
    ): AppointmentResponse {
        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { AppointmentNotFoundException("Appointment not found") }
        
        if (appointment.customerId != customerId) {
            throw AccessDeniedException("Access denied")
        }
        
        if (appointment.status == com.flowcare.backend.appointment.model.AppointmentStatus.CANCELLED) {
            throw com.flowcare.backend.appointment.exception.InvalidAppointmentStateException("Cannot reschedule cancelled appointment")
        }
        
        if (appointment.status == com.flowcare.backend.appointment.model.AppointmentStatus.COMPLETED) {
            throw com.flowcare.backend.appointment.exception.InvalidAppointmentStateException("Cannot reschedule completed appointment")
        }
        
        val newSlot = slotRepository.findById(newSlotId)
            .orElseThrow { SlotNotAvailableException("New slot not found") }
        
        if (!newSlot.isAvailable()) {
            throw SlotNotAvailableException("New slot is not available")
        }
        
        val oldSlotId = appointment.slotId
        
        appointment.slotId?.let { oldId ->
            slotRepository.findById(oldId).ifPresent { oldSlot ->
                oldSlot.bookedCount = maxOf(0, oldSlot.bookedCount - 1)
                oldSlot.updatedAt = LocalDateTime.now()
                slotRepository.save(oldSlot)
            }
        }
        
        newSlot.bookedCount++
        newSlot.updatedAt = LocalDateTime.now()
        
        val updatedAppointment = appointment.copy(
            branchId = newSlot.branchId,
            serviceTypeId = newSlot.serviceTypeId,
            slotId = newSlot.id,
            staffId = newSlot.staffId,
            updatedAt = LocalDateTime.now()
        )
        
        try {
            slotRepository.save(newSlot)
            val saved = appointmentRepository.save(updatedAppointment)
            
            auditLogService.log(
                actorId = customerId,
                actorRole = userRole,
                actionType = "APPOINTMENT_RESCHEDULED",
                entityType = "Appointment",
                entityId = saved.id,
                metadata = mapOf(
                    "oldSlotId" to (oldSlotId ?: ""),
                    "newSlotId" to newSlot.id,
                    "oldBranch" to appointment.branchId,
                    "newBranch" to newSlot.branchId,
                    "oldService" to appointment.serviceTypeId,
                    "newService" to newSlot.serviceTypeId,
                    "rescheduledAt" to LocalDateTime.now().toString()
                )
            )
            
            return toResponse(saved, newSlot)
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw SlotNotAvailableException("New slot was just booked by another customer")
        }
    }
    
    private fun toResponse(appointment: Appointment, slot: com.flowcare.backend.slot.model.Slot?) = 
        AppointmentResponse(
            id = appointment.id,
            customerId = appointment.customerId,
            branchId = appointment.branchId,
            serviceTypeId = appointment.serviceTypeId,
            slotId = appointment.slotId,
            staffId = appointment.staffId,
            status = appointment.status,
            startAt = slot?.startAt,
            endAt = slot?.endAt,
            hasAttachment = appointment.attachmentPath != null,
            createdAt = appointment.createdAt.toString()
        )
}
