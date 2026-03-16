package com.flowcare.backend.appointment.controller

import com.flowcare.backend.appointment.dto.BookAppointmentRequest
import com.flowcare.backend.appointment.dto.RescheduleAppointmentRequest
import com.flowcare.backend.appointment.dto.AppointmentResponse
import com.flowcare.backend.appointment.service.AppointmentService
import com.flowcare.backend.auth.service.BranchAccessService
import jakarta.validation.Valid
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files

@RestController
@RequestMapping("/api/appointments")
class AppointmentController(
    private val appointmentService: AppointmentService,
    private val branchAccessService: BranchAccessService
) {
    
    @PostMapping("/book")
    @PreAuthorize("hasRole('CUSTOMER')")
    fun bookAppointment(
        @Valid @RequestPart("request") request: BookAppointmentRequest,
        @RequestPart("attachment", required = false) attachment: MultipartFile?
    ): ResponseEntity<AppointmentResponse> {
        val customerId = branchAccessService.getCurrentUserId()!!
        val response = appointmentService.bookAppointment(
            customerId = customerId,
            slotId = request.slotId,
            attachment = attachment,
            userRole = "CUSTOMER"
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    fun getMyAppointments(): ResponseEntity<List<AppointmentResponse>> {
        val customerId = branchAccessService.getCurrentUserId()!!
        val appointments = appointmentService.getCustomerAppointments(customerId)
        return ResponseEntity.ok(appointments)
    }

    @GetMapping("/{appointmentId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    fun getAppointmentDetails(@PathVariable appointmentId: String): ResponseEntity<AppointmentResponse> {
        val customerId = branchAccessService.getCurrentUserId()!!
        val appointment = appointmentService.getAppointmentDetails(appointmentId, customerId)
        return ResponseEntity.ok(appointment)
    }

    @GetMapping("/{appointmentId}/attachment")
    @PreAuthorize("hasRole('CUSTOMER')")
    fun downloadAttachment(@PathVariable appointmentId: String): ResponseEntity<Resource> {
        val customerId = branchAccessService.getCurrentUserId()!!
        val file = appointmentService.getAttachment(appointmentId, customerId)
        val resource = UrlResource(file.toUri())
        
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(Files.probeContentType(file) ?: "application/octet-stream"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.fileName}\"")
            .body(resource)
    }

    @PostMapping("/{appointmentId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    fun cancelAppointment(@PathVariable appointmentId: String): ResponseEntity<AppointmentResponse> {
        val customerId = branchAccessService.getCurrentUserId()!!
        val response = appointmentService.cancelAppointment(appointmentId, customerId, "CUSTOMER")
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{appointmentId}/reschedule")
    @PreAuthorize("hasRole('CUSTOMER')")
    fun rescheduleAppointment(
        @PathVariable appointmentId: String,
        @Valid @RequestBody request: RescheduleAppointmentRequest
    ): ResponseEntity<AppointmentResponse> {
        val customerId = branchAccessService.getCurrentUserId()!!
        val response = appointmentService.rescheduleAppointment(
            appointmentId = appointmentId,
            customerId = customerId,
            newSlotId = request.newSlotId,
            userRole = "CUSTOMER"
        )
        return ResponseEntity.ok(response)
    }
}
