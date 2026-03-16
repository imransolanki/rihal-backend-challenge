# Staff & Appointment Management (Stories 14-17) Implementation Plan

## Overview
Implement staff schedule viewing, appointment status updates, manager/admin appointment listing with filters, and staff-to-service assignment management with role-based access control.

## Architecture
Staff views appointments through slots they're assigned to → Staff updates appointment status with workflow validation → Managers/Admins list appointments with comprehensive filters (branch-scoped for managers) → Managers/Admins assign staff to service types with branch validation → All operations audit logged

## Implementation Phases

### Phase 1: Staff Schedule Viewing (Story 14)
**Files**: 
- `src/main/kotlin/com/flowcare/backend/appointment/controller/StaffAppointmentController.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/service/StaffAppointmentService.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/repository/AppointmentRepository.kt`
- `src/test/kotlin/com/flowcare/backend/appointment/controller/StaffAppointmentControllerTest.kt`

Staff can view appointments in slots assigned to them, with date range and status filtering.

**Key code changes:**
```kotlin
// appointment/repository/AppointmentRepository.kt
@Query("""
    SELECT a FROM Appointment a 
    JOIN Slot s ON a.slotId = s.id 
    WHERE s.staffId = :staffId 
    AND a.status IN :statuses
    AND s.startAt BETWEEN :startDate AND :endDate
    ORDER BY s.startAt ASC
""")
fun findByStaffIdAndDateRange(
    staffId: String,
    statuses: List<AppointmentStatus>,
    startDate: OffsetDateTime,
    endDate: OffsetDateTime
): List<Appointment>

// appointment/service/StaffAppointmentService.kt
class StaffAppointmentService(
    private val appointmentRepository: AppointmentRepository,
    private val slotRepository: SlotRepository,
    private val userRepository: UserRepository,
    private val serviceTypeRepository: ServiceTypeRepository,
    private val branchRepository: BranchRepository
) {
    fun getStaffSchedule(
        staffId: String,
        startDate: LocalDate?,
        endDate: LocalDate?,
        statuses: List<AppointmentStatus>?
    ): List<AppointmentResponse> {
        val start = (startDate ?: LocalDate.now()).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime()
        val end = (endDate ?: LocalDate.now().plusDays(7)).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toOffsetDateTime()
        val filterStatuses = statuses ?: AppointmentStatus.values().toList()
        
        return appointmentRepository.findByStaffIdAndDateRange(staffId, filterStatuses, start, end)
            .map { toResponse(it) }
    }
}

// appointment/controller/StaffAppointmentController.kt
@RestController
@RequestMapping("/api/staff/appointments")
class StaffAppointmentController(
    private val staffAppointmentService: StaffAppointmentService,
    private val branchAccessService: BranchAccessService
) {
    @GetMapping("/my-schedule")
    @PreAuthorize("hasRole('STAFF')")
    fun getMySchedule(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(required = false) statuses: List<AppointmentStatus>?
    ): ResponseEntity<List<AppointmentResponse>> {
        val staffId = branchAccessService.getCurrentUserId()!!
        return ResponseEntity.ok(staffAppointmentService.getStaffSchedule(staffId, startDate, endDate, statuses))
    }
}
```

**Test cases:**
```kotlin
// StaffAppointmentControllerTest.kt
@Test
fun `staff should see appointments in their assigned slots`()

@Test
fun `staff should filter schedule by date range`()

@Test
fun `staff should filter schedule by status`()

@Test
fun `staff should not see appointments from other staff slots`()

@Test
fun `staff should see empty schedule when no slots assigned`()
```

**Technical details:**
- Default date range: today to +7 days if not specified
- Default statuses: all statuses if not specified
- Join appointments with slots to filter by slot.staffId
- Return full AppointmentResponse with customer, service, branch details

---

### Phase 2: Appointment Status Update (Story 15)
**Files**:
- `src/main/kotlin/com/flowcare/backend/appointment/dto/UpdateAppointmentStatusRequest.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/service/StaffAppointmentService.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/exception/InvalidStatusTransitionException.kt`
- `src/main/kotlin/com/flowcare/backend/common/exception/GlobalExceptionHandler.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/controller/StaffAppointmentController.kt`
- `src/test/kotlin/com/flowcare/backend/appointment/controller/StaffAppointmentControllerTest.kt`

Staff can update appointment status following workflow: BOOKED → CHECKED_IN → COMPLETED/NO_SHOW, with optional internal notes.

**Key code changes:**
```kotlin
// appointment/dto/UpdateAppointmentStatusRequest.kt
data class UpdateAppointmentStatusRequest(
    @field:NotNull(message = "Status is required")
    val status: AppointmentStatus,
    val internalNotes: String? = null
)

// appointment/exception/InvalidStatusTransitionException.kt
class InvalidStatusTransitionException(message: String) : RuntimeException(message)

// appointment/service/StaffAppointmentService.kt
@Transactional
@CacheEvict(value = ["availableSlots"], allEntries = true)
fun updateAppointmentStatus(
    appointmentId: String,
    staffId: String,
    request: UpdateAppointmentStatusRequest
): AppointmentResponse {
    val appointment = appointmentRepository.findById(appointmentId)
        .orElseThrow { AppointmentNotFoundException("Appointment not found: $appointmentId") }
    
    val staff = userRepository.findById(staffId).orElseThrow()
    if (appointment.branchId != staff.branchId)
        throw AccessDeniedException("Cannot update appointment in different branch")
    
    validateStatusTransition(appointment.status, request.status)
    
    val updated = appointment.copy(
        status = request.status,
        internalNotes = request.internalNotes ?: appointment.internalNotes,
        updatedAt = LocalDateTime.now()
    )
    val saved = appointmentRepository.save(updated)
    
    auditLogService.log(
        actorId = staffId, actorRole = "STAFF",
        actionType = "APPOINTMENT_STATUS_UPDATED", entityType = "Appointment", entityId = saved.id,
        metadata = mapOf(
            "oldStatus" to appointment.status.name, "newStatus" to saved.status.name,
            "branchId" to saved.branchId, "hasNotes" to (request.internalNotes != null)
        )
    )
    return toResponse(saved)
}

private fun validateStatusTransition(current: AppointmentStatus, new: AppointmentStatus) {
    val allowed = when (current) {
        AppointmentStatus.BOOKED -> listOf(AppointmentStatus.CHECKED_IN)
        AppointmentStatus.CHECKED_IN -> listOf(AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW)
        AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW, AppointmentStatus.CANCELLED -> emptyList()
    }
    if (new !in allowed)
        throw InvalidStatusTransitionException("Cannot transition from $current to $new")
}

// appointment/controller/StaffAppointmentController.kt
@PatchMapping("/{appointmentId}/status")
@PreAuthorize("hasRole('STAFF')")
fun updateAppointmentStatus(
    @PathVariable appointmentId: String,
    @Valid @RequestBody request: UpdateAppointmentStatusRequest
): ResponseEntity<AppointmentResponse> {
    val staffId = branchAccessService.getCurrentUserId()!!
    return ResponseEntity.ok(staffAppointmentService.updateAppointmentStatus(appointmentId, staffId, request))
}

// common/exception/GlobalExceptionHandler.kt
@ExceptionHandler(InvalidStatusTransitionException::class)
fun handleInvalidStatusTransition(ex: InvalidStatusTransitionException): ResponseEntity<ErrorResponse> {
    return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.message ?: "Invalid status transition")
}
```

**Test cases:**
```kotlin
@Test
fun `staff should update BOOKED to CHECKED_IN`()

@Test
fun `staff should update CHECKED_IN to COMPLETED`()

@Test
fun `staff should update CHECKED_IN to NO_SHOW`()

@Test
fun `staff should reject invalid transition BOOKED to COMPLETED`()

@Test
fun `staff should reject updating CANCELLED appointment`()

@Test
fun `staff should reject updating appointment in different branch`()

@Test
fun `staff should add internal notes when updating status`()

@Test
fun `audit log should be created on status update`()
```

**Technical details:**
- Workflow: BOOKED → CHECKED_IN → (COMPLETED | NO_SHOW)
- Terminal states (COMPLETED, NO_SHOW, CANCELLED) cannot be changed
- Staff can only update appointments in their own branch
- Internal notes are optional and append/replace existing notes

---

### Phase 3: Manager/Admin Appointment Listing (Story 16)
**Files**:
- `src/main/kotlin/com/flowcare/backend/appointment/controller/AppointmentManagementController.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/service/AppointmentManagementService.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/repository/AppointmentRepository.kt`
- `src/test/kotlin/com/flowcare/backend/appointment/controller/AppointmentManagementControllerTest.kt`

Managers/Admins list appointments with comprehensive filtering (status, date range, branch, service type, staff), with role-based scoping.

**Key code changes:**
```kotlin
// appointment/repository/AppointmentRepository.kt
@Query("""
    SELECT a FROM Appointment a 
    WHERE (:branchId IS NULL OR a.branchId = :branchId)
    AND (:serviceTypeId IS NULL OR a.serviceTypeId = :serviceTypeId)
    AND (:staffId IS NULL OR a.slotId IN (SELECT s.id FROM Slot s WHERE s.staffId = :staffId))
    AND (:status IS NULL OR a.status = :status)
    AND (:startDate IS NULL OR a.createdAt >= :startDate)
    AND (:endDate IS NULL OR a.createdAt <= :endDate)
    ORDER BY a.createdAt DESC
""")
fun findByFilters(
    branchId: String?,
    serviceTypeId: String?,
    staffId: String?,
    status: AppointmentStatus?,
    startDate: LocalDateTime?,
    endDate: LocalDateTime?
): List<Appointment>

// appointment/service/AppointmentManagementService.kt
class AppointmentManagementService(
    private val appointmentRepository: AppointmentRepository,
    private val branchAccessService: BranchAccessService,
    private val userRepository: UserRepository,
    private val serviceTypeRepository: ServiceTypeRepository,
    private val branchRepository: BranchRepository
) {
    fun listAppointments(
        branchId: String?,
        serviceTypeId: String?,
        staffId: String?,
        status: AppointmentStatus?,
        startDate: LocalDate?,
        endDate: LocalDate?
    ): List<AppointmentResponse> {
        val effectiveBranchId = if (branchAccessService.isBranchManager()) 
            branchAccessService.getCurrentUserBranchId() else branchId
        
        val start = startDate?.atStartOfDay()
        val end = endDate?.atTime(23, 59, 59)
        
        return appointmentRepository.findByFilters(
            effectiveBranchId, serviceTypeId, staffId, status, start, end
        ).map { toResponse(it) }
    }
    
    private fun toResponse(appointment: Appointment): AppointmentResponse {
        val customer = userRepository.findById(appointment.customerId).orElseThrow()
        val service = serviceTypeRepository.findById(appointment.serviceTypeId).orElseThrow()
        val branch = branchRepository.findById(appointment.branchId).orElseThrow()
        
        return AppointmentResponse(
            id = appointment.id, customerId = appointment.customerId,
            customerName = customer.fullName, branchId = appointment.branchId,
            branchName = branch.name, serviceTypeId = appointment.serviceTypeId,
            serviceTypeName = service.name, slotId = appointment.slotId,
            status = appointment.status, hasAttachment = appointment.attachmentPath != null,
            createdAt = appointment.createdAt, updatedAt = appointment.updatedAt
        )
    }
}

// appointment/controller/AppointmentManagementController.kt
@RestController
@RequestMapping("/api/appointments/manage")
class AppointmentManagementController(
    private val appointmentManagementService: AppointmentManagementService
) {
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun listAppointments(
        @RequestParam(required = false) branchId: String?,
        @RequestParam(required = false) serviceTypeId: String?,
        @RequestParam(required = false) staffId: String?,
        @RequestParam(required = false) status: AppointmentStatus?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?
    ): ResponseEntity<List<AppointmentResponse>> {
        return ResponseEntity.ok(appointmentManagementService.listAppointments(
            branchId, serviceTypeId, staffId, status, startDate, endDate
        ))
    }
}
```

**Test cases:**
```kotlin
@Test
fun `admin should list all appointments across branches`()

@Test
fun `manager should list only their branch appointments`()

@Test
fun `manager cannot override branch filter`()

@Test
fun `should filter appointments by status`()

@Test
fun `should filter appointments by date range`()

@Test
fun `should filter appointments by service type`()

@Test
fun `should filter appointments by staff`()

@Test
fun `should combine multiple filters`()

@Test
fun `should return appointments with full details`()
```

**Technical details:**
- Branch managers automatically scoped to their branch (branchId param ignored)
- Admins can filter by any branch or see all branches (branchId = null)
- All filters are optional and combinable
- Staff filter uses slot.staffId (appointments in slots assigned to that staff)
- Date range filters by appointment.createdAt

---

### Phase 4: Staff-Service Assignment (Story 17)
**Files**:
- `src/main/kotlin/com/flowcare/backend/staff/dto/AssignStaffRequest.kt`
- `src/main/kotlin/com/flowcare/backend/staff/dto/StaffServiceAssignmentResponse.kt`
- `src/main/kotlin/com/flowcare/backend/staff/service/StaffAssignmentService.kt`
- `src/main/kotlin/com/flowcare/backend/staff/exception/DuplicateAssignmentException.kt`
- `src/main/kotlin/com/flowcare/backend/staff/exception/BranchMismatchException.kt`
- `src/main/kotlin/com/flowcare/backend/staff/controller/StaffAssignmentController.kt`
- `src/main/kotlin/com/flowcare/backend/common/exception/GlobalExceptionHandler.kt`
- `src/test/kotlin/com/flowcare/backend/staff/controller/StaffAssignmentControllerTest.kt`

Managers/Admins assign staff to service types with branch validation, prevent duplicates, and support unassignment.

**Key code changes:**
```kotlin
// staff/dto/AssignStaffRequest.kt
data class AssignStaffRequest(
    @field:NotBlank(message = "Staff ID is required")
    val staffId: String,
    @field:NotBlank(message = "Service type ID is required")
    val serviceTypeId: String
)

// staff/dto/StaffServiceAssignmentResponse.kt
data class StaffServiceAssignmentResponse(
    val id: Int,
    val staffId: String,
    val staffName: String,
    val serviceTypeId: String,
    val serviceTypeName: String,
    val branchId: String,
    val createdAt: LocalDateTime
)

// staff/exception/DuplicateAssignmentException.kt
class DuplicateAssignmentException(message: String) : RuntimeException(message)

// staff/exception/BranchMismatchException.kt
class BranchMismatchException(message: String) : RuntimeException(message)

// staff/service/StaffAssignmentService.kt
@Service
class StaffAssignmentService(
    private val staffServiceTypeRepository: StaffServiceTypeRepository,
    private val userRepository: UserRepository,
    private val serviceTypeRepository: ServiceTypeRepository,
    private val auditLogService: AuditLogService
) {
    @Transactional
    fun assignStaffToService(
        request: AssignStaffRequest,
        actorId: String,
        actorRole: String
    ): StaffServiceAssignmentResponse {
        val staff = userRepository.findById(request.staffId)
            .orElseThrow { IllegalArgumentException("Staff not found: ${request.staffId}") }
        
        if (staff.role != Role.STAFF)
            throw IllegalArgumentException("User is not a staff member")
        
        val service = serviceTypeRepository.findById(request.serviceTypeId)
            .orElseThrow { IllegalArgumentException("Service type not found: ${request.serviceTypeId}") }
        
        if (staff.branchId != service.branchId)
            throw BranchMismatchException("Staff and service must belong to the same branch")
        
        if (staffServiceTypeRepository.existsByStaffIdAndServiceTypeId(request.staffId, request.serviceTypeId))
            throw DuplicateAssignmentException("Staff already assigned to this service type")
        
        val assignment = staffServiceTypeRepository.save(StaffServiceType(
            staffId = request.staffId,
            serviceTypeId = request.serviceTypeId
        ))
        
        auditLogService.log(
            actorId = actorId, actorRole = actorRole,
            actionType = "STAFF_ASSIGNED_TO_SERVICE", entityType = "StaffServiceType", entityId = assignment.id.toString(),
            metadata = mapOf(
                "staffId" to staff.id, "staffName" to staff.fullName,
                "serviceTypeId" to service.id, "serviceTypeName" to service.name,
                "branchId" to staff.branchId!!
            )
        )
        
        return StaffServiceAssignmentResponse(
            id = assignment.id!!, staffId = staff.id, staffName = staff.fullName,
            serviceTypeId = service.id, serviceTypeName = service.name,
            branchId = staff.branchId!!, createdAt = assignment.createdAt
        )
    }
    
    @Transactional
    fun unassignStaffFromService(
        staffId: String,
        serviceTypeId: String,
        actorId: String,
        actorRole: String
    ) {
        val assignment = staffServiceTypeRepository.findByStaffIdAndServiceTypeId(staffId, serviceTypeId)
            ?: throw IllegalArgumentException("Assignment not found")
        
        staffServiceTypeRepository.delete(assignment)
        
        auditLogService.log(
            actorId = actorId, actorRole = actorRole,
            actionType = "STAFF_UNASSIGNED_FROM_SERVICE", entityType = "StaffServiceType", entityId = assignment.id.toString(),
            metadata = mapOf("staffId" to staffId, "serviceTypeId" to serviceTypeId)
        )
    }
    
    fun listStaffAssignments(branchId: String?): List<StaffServiceAssignmentResponse> {
        val assignments = if (branchId != null)
            staffServiceTypeRepository.findByBranchId(branchId)
        else
            staffServiceTypeRepository.findAll()
        
        return assignments.map { assignment ->
            val staff = userRepository.findById(assignment.staffId).orElseThrow()
            val service = serviceTypeRepository.findById(assignment.serviceTypeId).orElseThrow()
            StaffServiceAssignmentResponse(
                id = assignment.id!!, staffId = staff.id, staffName = staff.fullName,
                serviceTypeId = service.id, serviceTypeName = service.name,
                branchId = staff.branchId!!, createdAt = assignment.createdAt
            )
        }
    }
}

// staff/controller/StaffAssignmentController.kt
@RestController
@RequestMapping("/api/staff/assignments")
class StaffAssignmentController(
    private val staffAssignmentService: StaffAssignmentService,
    private val branchAccessService: BranchAccessService
) {
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun assignStaffToService(@Valid @RequestBody request: AssignStaffRequest): ResponseEntity<StaffServiceAssignmentResponse> {
        val response = staffAssignmentService.assignStaffToService(
            request, branchAccessService.getCurrentUserId()!!, getCurrentRole()
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
    
    @DeleteMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun unassignStaffFromService(
        @RequestParam staffId: String,
        @RequestParam serviceTypeId: String
    ): ResponseEntity<Void> {
        staffAssignmentService.unassignStaffFromService(
            staffId, serviceTypeId, branchAccessService.getCurrentUserId()!!, getCurrentRole()
        )
        return ResponseEntity.noContent().build()
    }
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun listAssignments(@RequestParam(required = false) branchId: String?): ResponseEntity<List<StaffServiceAssignmentResponse>> {
        val effectiveBranchId = if (branchAccessService.isBranchManager())
            branchAccessService.getCurrentUserBranchId() else branchId
        return ResponseEntity.ok(staffAssignmentService.listStaffAssignments(effectiveBranchId))
    }
    
    private fun getCurrentRole() = when {
        branchAccessService.isAdmin() -> "ADMIN"
        branchAccessService.isBranchManager() -> "BRANCH_MANAGER"
        else -> "UNKNOWN"
    }
}

// staff/repository/StaffServiceTypeRepository.kt (extend existing)
fun findByStaffIdAndServiceTypeId(staffId: String, serviceTypeId: String): StaffServiceType?

@Query("SELECT s FROM StaffServiceType s WHERE s.staffId IN (SELECT u.id FROM User u WHERE u.branchId = :branchId)")
fun findByBranchId(branchId: String): List<StaffServiceType>

// common/exception/GlobalExceptionHandler.kt
@ExceptionHandler(DuplicateAssignmentException::class)
fun handleDuplicateAssignment(ex: DuplicateAssignmentException): ResponseEntity<ErrorResponse> {
    return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.message ?: "Duplicate assignment")
}

@ExceptionHandler(BranchMismatchException::class)
fun handleBranchMismatch(ex: BranchMismatchException): ResponseEntity<ErrorResponse> {
    return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.message ?: "Branch mismatch")
}
```

**Test cases:**
```kotlin
@Test
fun `admin should assign staff to service in same branch`()

@Test
fun `manager should assign staff to service in own branch`()

@Test
fun `should reject assignment when staff and service in different branches`()

@Test
fun `should reject duplicate assignment`()

@Test
fun `should reject assignment of non-staff user`()

@Test
fun `admin should unassign staff from service`()

@Test
fun `should reject unassignment of non-existent assignment`()

@Test
fun `admin should list all assignments`()

@Test
fun `manager should list only their branch assignments`()

@Test
fun `audit log should be created on assignment`()

@Test
fun `audit log should be created on unassignment`()
```

**Technical details:**
- Validate staff.branchId == service.branchId before assignment
- Prevent duplicate assignments using existsByStaffIdAndServiceTypeId
- Branch managers automatically scoped to their branch
- DELETE endpoint uses query params (staffId, serviceTypeId)
- List endpoint returns full details (staff name, service name, branch)

---

## Technical Considerations

**Dependencies:**
- No new packages required - uses existing Spring Security, JPA, validation

**Edge Cases:**
- Staff with no slot assignments see empty schedule
- Status transitions validated to prevent workflow violations
- Branch-scoped access enforced for all operations
- Duplicate assignments prevented at service layer

**Testing Strategy:**
- Unit tests for DTO validation (4 tests)
- Integration tests for each controller (32 tests total)
- Test role-based access control for all endpoints
- Test branch scoping for managers
- Test status transition validation
- Test audit log creation

**Performance:**
- Appointment listing uses indexed queries (branchId, status, createdAt)
- Staff schedule query joins appointments with slots efficiently
- Consider adding composite index on (slot.staffId, appointment.status)

**Security:**
- All endpoints require authentication
- Role-based authorization using @PreAuthorize
- Branch managers cannot access other branches
- Staff can only update appointments in their branch

## Success Criteria

- [ ] Staff can view their schedule with date/status filters
- [ ] Staff can update appointment status following workflow rules
- [ ] Staff cannot update appointments in other branches
- [ ] Managers can list appointments with comprehensive filters
- [ ] Managers are automatically scoped to their branch
- [ ] Admins can list appointments across all branches
- [ ] Managers/Admins can assign staff to services in same branch
- [ ] System prevents duplicate staff-service assignments
- [ ] System prevents cross-branch staff-service assignments
- [ ] Managers/Admins can unassign staff from services
- [ ] All operations create audit log entries
- [ ] Invalid status transitions are rejected
- [ ] All endpoints have comprehensive test coverage
