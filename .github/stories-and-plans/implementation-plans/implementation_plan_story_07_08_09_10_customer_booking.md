# Customer Booking Stories Implementation Plan

## Overview
Implement complete appointment booking lifecycle for customers: book appointments with optional attachments, view appointment history, cancel appointments, and reschedule to different slots. Includes audit logging, file storage, optimistic locking for concurrency, and role-based access control.

## Architecture
Customer → AppointmentController → AppointmentService → [SlotRepository, AppointmentRepository, FileStorageService, AuditLogService] → PostgreSQL + File System

Key components:
- AppointmentController: REST endpoints for booking operations
- AppointmentService: Business logic for CRUD operations with validation
- AuditLogService: Centralized audit logging for compliance
- FileStorageService: Extended to handle appointment attachments
- Optimistic locking (@Version) on Slot entity prevents concurrent booking conflicts

## Implementation Phases

### Phase 1: Foundation - Audit Service & Storage Extension
**Files**: 
- `src/main/kotlin/com/flowcare/backend/audit/service/AuditLogService.kt`
- `src/main/kotlin/com/flowcare/backend/storage/service/FileStorageService.kt`
- `src/main/kotlin/com/flowcare/backend/storage/config/StorageProperties.kt`
- `src/main/kotlin/com/flowcare/backend/slot/model/Slot.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/audit/service/AuditLogServiceTest.kt`
- `src/test/kotlin/com/flowcare/backend/storage/service/FileStorageServiceTest.kt`

Create reusable audit logging service and extend file storage to handle appointment attachments. Add optimistic locking to Slot entity.

**Key code changes:**

```kotlin
// audit/service/AuditLogService.kt
package com.flowcare.backend.audit.service

import com.flowcare.backend.audit.model.AuditLog
import com.flowcare.backend.audit.repository.AuditLogRepository
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.*

@Service
class AuditLogService(private val auditLogRepository: AuditLogRepository) {
    
    fun log(
        actorId: String,
        actorRole: String,
        actionType: String,
        entityType: String,
        entityId: String,
        metadata: Map<String, Any>? = null
    ) {
        val auditLog = AuditLog(
            id = "audit_${UUID.randomUUID()}",
            actorId = actorId,
            actorRole = actorRole,
            actionType = actionType,
            entityType = entityType,
            entityId = entityId,
            timestamp = OffsetDateTime.now(),
            metadata = metadata
        )
        auditLogRepository.save(auditLog)
    }
}

// storage/service/FileStorageService.kt - Add new method
fun storeAppointmentAttachment(file: MultipartFile, appointmentId: String): String {
    validateFile(file)
    val extension = getFileExtension(file.originalFilename ?: "")
    val fileName = "appt_${appointmentId}_${UUID.randomUUID()}.$extension"
    val attachmentPath = Paths.get(properties.uploadDir, "attachments").toAbsolutePath().normalize()
    
    Files.createDirectories(attachmentPath)
    Files.copy(file.inputStream, attachmentPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING)
    
    return "attachments/$fileName"
}

// storage/config/StorageProperties.kt - Add PDF support
data class StorageProperties(
    val uploadDir: String = "./uploads",
    val idDocumentsDir: String = "./uploads/id_documents",
    val maxFileSize: Long = 5242880,
    val allowedImageTypes: List<String> = listOf("image/jpeg", "image/png", "image/gif", "image/bmp", "application/pdf"),
    val allowedExtensions: List<String> = listOf("jpg", "jpeg", "png", "gif", "bmp", "pdf")
)

// slot/model/Slot.kt - Add optimistic locking
@Entity
@Table(name = "slots")
data class Slot(
    @Id
    val id: String,
    // ... existing fields ...
    
    @Version
    var version: Long = 0
)
```

**Test cases:**
- Test AuditLogService creates audit log with all required fields
- Test AuditLogService handles null metadata
- Test storeAppointmentAttachment creates file in attachments directory
- Test storeAppointmentAttachment validates file size and type
- Test storeAppointmentAttachment rejects invalid file formats
- Test storeAppointmentAttachment accepts PDF files
- Test optimistic locking throws exception on concurrent slot updates

**Technical details:**
- AuditLogService follows existing service pattern (SlotService, BranchAccessService)
- File storage reuses existing validation logic from storeIdDocument()
- Optimistic locking uses JPA @Version annotation for automatic conflict detection
- Attachments stored in separate directory: uploads/attachments/

---

### Phase 2: Book Appointment (Story 7)
**Files**: 
- `src/main/kotlin/com/flowcare/backend/appointment/dto/BookAppointmentRequest.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/dto/AppointmentResponse.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/service/AppointmentService.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/controller/AppointmentController.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/exception/SlotNotAvailableException.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/exception/AppointmentNotFoundException.kt`
- `src/main/kotlin/com/flowcare/backend/common/exception/GlobalExceptionHandler.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/appointment/service/AppointmentServiceTest.kt`
- `src/test/kotlin/com/flowcare/backend/appointment/controller/AppointmentControllerTest.kt`

Implement appointment booking with slot validation, optional attachment upload, and audit logging.

**Key code changes:**

```kotlin
// appointment/dto/BookAppointmentRequest.kt
package com.flowcare.backend.appointment.dto

import jakarta.validation.constraints.NotBlank

data class BookAppointmentRequest(
    @field:NotBlank(message = "Slot ID is required")
    val slotId: String
)

// appointment/dto/AppointmentResponse.kt
package com.flowcare.backend.appointment.dto

import com.flowcare.backend.appointment.model.AppointmentStatus
import java.time.OffsetDateTime

data class AppointmentResponse(
    val id: String,
    val customerId: String,
    val branchId: String,
    val serviceTypeId: String,
    val slotId: String?,
    val staffId: String?,
    val status: AppointmentStatus,
    val startAt: OffsetDateTime?,
    val endAt: OffsetDateTime?,
    val hasAttachment: Boolean,
    val createdAt: String
)

// appointment/service/AppointmentService.kt
package com.flowcare.backend.appointment.service

import com.flowcare.backend.appointment.dto.AppointmentResponse
import com.flowcare.backend.appointment.exception.SlotNotAvailableException
import com.flowcare.backend.appointment.model.Appointment
import com.flowcare.backend.appointment.repository.AppointmentRepository
import com.flowcare.backend.audit.service.AuditLogService
import com.flowcare.backend.slot.repository.SlotRepository
import com.flowcare.backend.storage.service.FileStorageService
import jakarta.transaction.Transactional
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
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

// appointment/controller/AppointmentController.kt
package com.flowcare.backend.appointment.controller

import com.flowcare.backend.appointment.dto.BookAppointmentRequest
import com.flowcare.backend.appointment.dto.AppointmentResponse
import com.flowcare.backend.appointment.service.AppointmentService
import com.flowcare.backend.auth.service.BranchAccessService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

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
}

// appointment/exception/SlotNotAvailableException.kt
package com.flowcare.backend.appointment.exception

class SlotNotAvailableException(message: String) : RuntimeException(message)

// common/exception/GlobalExceptionHandler.kt - Add handler
@ExceptionHandler(SlotNotAvailableException::class)
fun handleSlotNotAvailable(ex: SlotNotAvailableException): ResponseEntity<ErrorResponse> {
    return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.message ?: "Slot not available")
}
```

**Test cases:**
- Test bookAppointment creates appointment with valid slot
- Test bookAppointment increments slot bookedCount
- Test bookAppointment stores attachment when provided
- Test bookAppointment succeeds without attachment
- Test bookAppointment throws exception when slot already booked
- Test bookAppointment throws exception when slot soft-deleted
- Test bookAppointment creates audit log entry
- Test bookAppointment handles concurrent booking with optimistic locking
- Test controller requires CUSTOMER role
- Test controller validates request fields

**Technical details:**
- @Transactional ensures atomicity of slot update and appointment creation
- Optimistic locking on Slot prevents race conditions
- Attachment is optional via @RequestPart(required = false)
- Uses existing BranchAccessService.getCurrentUserId() pattern
- Follows existing controller pattern with @PreAuthorize

---

### Phase 3: View Appointments (Story 8)
**Files**: 
- `src/main/kotlin/com/flowcare/backend/appointment/service/AppointmentService.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/controller/AppointmentController.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/repository/AppointmentRepository.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/appointment/service/AppointmentServiceTest.kt`
- `src/test/kotlin/com/flowcare/backend/appointment/controller/AppointmentControllerTest.kt`

Implement viewing appointment list and details, with attachment download capability.

**Key code changes:**

```kotlin
// appointment/repository/AppointmentRepository.kt - Add query methods
interface AppointmentRepository : JpaRepository<Appointment, String> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: String): List<Appointment>
}

// appointment/service/AppointmentService.kt - Add methods
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

// appointment/controller/AppointmentController.kt - Add endpoints
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
        .contentType(MediaType.parseMediaType(Files.probeContentType(file)))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.fileName}\"")
        .body(resource)
}
```

**Test cases:**
- Test getCustomerAppointments returns all customer appointments sorted by date
- Test getCustomerAppointments returns empty list when no appointments
- Test getAppointmentDetails returns correct appointment with slot info
- Test getAppointmentDetails throws exception for other customer's appointment
- Test getAttachment returns file with correct content type
- Test getAttachment throws exception when no attachment exists
- Test getAttachment throws exception for other customer's appointment
- Test controller endpoints require CUSTOMER role

**Technical details:**
- Appointments sorted by createdAt DESC (newest first)
- Slot info joined for display (startAt, endAt)
- Access control enforced: customers only see their own appointments
- Attachment download uses Spring Resource with proper content-type headers

---

### Phase 4: Cancel Appointment (Story 9)
**Files**: 
- `src/main/kotlin/com/flowcare/backend/appointment/service/AppointmentService.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/controller/AppointmentController.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/exception/InvalidAppointmentStateException.kt`
- `src/main/kotlin/com/flowcare/backend/common/exception/GlobalExceptionHandler.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/appointment/service/AppointmentServiceTest.kt`
- `src/test/kotlin/com/flowcare/backend/appointment/controller/AppointmentControllerTest.kt`

Implement appointment cancellation with slot release and audit logging.

**Key code changes:**

```kotlin
// appointment/service/AppointmentService.kt - Add method
@Transactional
fun cancelAppointment(appointmentId: String, customerId: String, userRole: String): AppointmentResponse {
    val appointment = appointmentRepository.findById(appointmentId)
        .orElseThrow { AppointmentNotFoundException("Appointment not found") }
    
    if (appointment.customerId != customerId) {
        throw AccessDeniedException("Access denied")
    }
    
    if (appointment.status == AppointmentStatus.CANCELLED) {
        throw InvalidAppointmentStateException("Appointment is already cancelled")
    }
    
    if (appointment.status == AppointmentStatus.COMPLETED) {
        throw InvalidAppointmentStateException("Cannot cancel completed appointment")
    }
    
    val updatedAppointment = appointment.copy(
        status = AppointmentStatus.CANCELLED,
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

// appointment/controller/AppointmentController.kt - Add endpoint
@PostMapping("/{appointmentId}/cancel")
@PreAuthorize("hasRole('CUSTOMER')")
fun cancelAppointment(@PathVariable appointmentId: String): ResponseEntity<AppointmentResponse> {
    val customerId = branchAccessService.getCurrentUserId()!!
    val response = appointmentService.cancelAppointment(appointmentId, customerId, "CUSTOMER")
    return ResponseEntity.ok(response)
}

// appointment/exception/InvalidAppointmentStateException.kt
package com.flowcare.backend.appointment.exception

class InvalidAppointmentStateException(message: String) : RuntimeException(message)

// common/exception/GlobalExceptionHandler.kt - Add handler
@ExceptionHandler(InvalidAppointmentStateException::class)
fun handleInvalidAppointmentState(ex: InvalidAppointmentStateException): ResponseEntity<ErrorResponse> {
    return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.message ?: "Invalid appointment state")
}
```

**Test cases:**
- Test cancelAppointment updates status to CANCELLED
- Test cancelAppointment decrements slot bookedCount
- Test cancelAppointment creates audit log entry
- Test cancelAppointment throws exception when already cancelled
- Test cancelAppointment throws exception when completed
- Test cancelAppointment throws exception for other customer's appointment
- Test cancelAppointment handles missing slot gracefully
- Test controller requires CUSTOMER role

**Technical details:**
- @Transactional ensures atomicity of status update and slot release
- Slot bookedCount decremented to make slot available again
- Cancelled appointments remain in history (not deleted)
- Access control: customers can only cancel their own appointments

---

### Phase 5: Reschedule Appointment (Story 10)
**Files**: 
- `src/main/kotlin/com/flowcare/backend/appointment/dto/RescheduleAppointmentRequest.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/service/AppointmentService.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/controller/AppointmentController.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/appointment/service/AppointmentServiceTest.kt`
- `src/test/kotlin/com/flowcare/backend/appointment/controller/AppointmentControllerTest.kt`

Implement appointment rescheduling with slot validation and audit logging.

**Key code changes:**

```kotlin
// appointment/dto/RescheduleAppointmentRequest.kt
package com.flowcare.backend.appointment.dto

import jakarta.validation.constraints.NotBlank

data class RescheduleAppointmentRequest(
    @field:NotBlank(message = "New slot ID is required")
    val newSlotId: String
)

// appointment/service/AppointmentService.kt - Add method
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
    
    if (appointment.status == AppointmentStatus.CANCELLED) {
        throw InvalidAppointmentStateException("Cannot reschedule cancelled appointment")
    }
    
    if (appointment.status == AppointmentStatus.COMPLETED) {
        throw InvalidAppointmentStateException("Cannot reschedule completed appointment")
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

// appointment/controller/AppointmentController.kt - Add endpoint
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
```

**Test cases:**
- Test rescheduleAppointment updates to new slot
- Test rescheduleAppointment releases old slot (decrements bookedCount)
- Test rescheduleAppointment books new slot (increments bookedCount)
- Test rescheduleAppointment updates branch and service if different
- Test rescheduleAppointment creates audit log with old and new values
- Test rescheduleAppointment throws exception when new slot unavailable
- Test rescheduleAppointment throws exception when appointment cancelled
- Test rescheduleAppointment throws exception when appointment completed
- Test rescheduleAppointment throws exception for other customer's appointment
- Test rescheduleAppointment handles concurrent booking with optimistic locking
- Test controller requires CUSTOMER role

**Technical details:**
- @Transactional ensures atomicity of old slot release and new slot booking
- Optimistic locking prevents concurrent booking of new slot
- Appointment can change branch/service when rescheduling
- Attachment remains associated with appointment after reschedule
- Audit log captures both old and new slot details

---

## Technical Considerations

**Dependencies:**
- No new external dependencies required
- Uses existing Spring Boot, JPA, Spring Security, Validation

**Edge Cases:**
- Concurrent booking: Handled by optimistic locking (@Version on Slot)
- Slot deleted after viewing: Checked in isAvailable() method
- Missing slot reference: Handled gracefully (slot info shows null)
- File upload failures: Wrapped in FileStorageException
- Access control: Enforced at service layer and controller with @PreAuthorize

**Testing Strategy:**
- Unit tests for service layer with mocked repositories
- Integration tests for controllers with @SpringBootTest and MockMvc
- Test concurrent scenarios with optimistic locking
- Test file upload/download with multipart requests
- Test audit log creation for all operations

**Performance:**
- Slot queries use existing indexes on branchId, serviceTypeId
- Appointment queries indexed on customerId
- File storage uses filesystem (no database overhead)
- Audit logs written asynchronously (consider @Async if needed)

**Security:**
- Role-based access: Only CUSTOMER role can book/cancel/reschedule
- Ownership validation: Customers can only access their own appointments
- File validation: Size limits (5MB) and type restrictions enforced
- Audit logging: All sensitive operations logged for compliance

---

## Success Criteria

- [ ] Customers can book appointments with valid slots
- [ ] Customers can optionally upload attachments (images/PDFs up to 5MB)
- [ ] Slot bookedCount increments on booking and prevents overbooking
- [ ] Concurrent booking attempts handled gracefully with optimistic locking
- [ ] Customers can view their appointment list sorted by date
- [ ] Customers can view individual appointment details with slot info
- [ ] Customers can download their uploaded attachments
- [ ] Customers can cancel appointments (status changes, slot released)
- [ ] Customers can reschedule to different slots (old released, new booked)
- [ ] Audit logs created for booking, cancellation, and rescheduling
- [ ] Access control enforced: customers only access their own appointments
- [ ] All endpoints require CUSTOMER role authentication
- [ ] Validation errors return clear messages
- [ ] File storage errors handled gracefully
- [ ] All test cases pass with >80% coverage
