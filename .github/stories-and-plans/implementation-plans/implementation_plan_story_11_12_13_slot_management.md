# Slot Management (Stories 11, 12, 13) Implementation Plan

## Overview
Implement comprehensive slot management capabilities for Admins and Branch Managers to create, update, and soft-delete appointment slots with role-based access control, audit logging, and cache invalidation.

## Architecture
Admin/Branch Manager → REST API → SlotManagementService → SlotRepository → PostgreSQL
                                  ↓
                            AuditLogService → AuditLogRepository
                                  ↓
                            Cache Invalidation (@CacheEvict)

## Implementation Phases

### Phase 1: DTOs and Validation
**Files**: 
- `src/main/kotlin/com/flowcare/backend/slot/dto/CreateSlotRequest.kt`
- `src/main/kotlin/com/flowcare/backend/slot/dto/BulkCreateSlotsRequest.kt`
- `src/main/kotlin/com/flowcare/backend/slot/dto/UpdateSlotRequest.kt`
- `src/main/kotlin/com/flowcare/backend/slot/dto/SlotResponse.kt`
- `src/main/kotlin/com/flowcare/backend/slot/dto/BulkCreateSlotsResponse.kt`
- `src/main/kotlin/com/flowcare/backend/slot/exception/SlotValidationException.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/slot/dto/CreateSlotRequestTest.kt`

Create DTOs for slot management operations with validation annotations.

**Key code changes:**
```kotlin
// CreateSlotRequest.kt
package com.flowcare.backend.slot.dto

import jakarta.validation.constraints.*
import java.time.OffsetDateTime

data class CreateSlotRequest(
    @field:NotBlank(message = "Branch ID is required")
    val branchId: String,
    
    @field:NotBlank(message = "Service type ID is required")
    val serviceTypeId: String,
    
    val staffId: String? = null,
    
    @field:NotNull(message = "Start time is required")
    val startAt: OffsetDateTime,
    
    @field:NotNull(message = "End time is required")
    val endAt: OffsetDateTime,
    
    @field:Min(value = 1, message = "Capacity must be at least 1")
    val capacity: Int = 1
)

// BulkCreateSlotsRequest.kt
data class BulkCreateSlotsRequest(
    @field:NotEmpty(message = "Slots list cannot be empty")
    @field:Size(max = 100, message = "Cannot create more than 100 slots at once")
    val slots: List<CreateSlotRequest>
)

// UpdateSlotRequest.kt
data class UpdateSlotRequest(
    val startAt: OffsetDateTime? = null,
    val endAt: OffsetDateTime? = null,
    val staffId: String? = null,
    val removeStaffAssignment: Boolean = false,
    val capacity: Int? = null
)

// SlotResponse.kt
data class SlotResponse(
    val id: String,
    val branchId: String,
    val serviceTypeId: String,
    val staffId: String?,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val capacity: Int,
    val bookedCount: Int,
    val isActive: Boolean,
    val deletedAt: OffsetDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

// BulkCreateSlotsResponse.kt
data class BulkCreateSlotsResponse(
    val successCount: Int,
    val failureCount: Int,
    val createdSlots: List<SlotResponse>,
    val failures: List<SlotCreationFailure>
)

data class SlotCreationFailure(
    val index: Int,
    val request: CreateSlotRequest,
    val reason: String
)

// SlotValidationException.kt
class SlotValidationException(message: String) : RuntimeException(message)
```

**Test cases:**
```kotlin
// CreateSlotRequestTest.kt
class CreateSlotRequestTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator
    
    @Test
    fun `should validate valid request`() {
        val request = CreateSlotRequest(
            branchId = "branch1",
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1),
            capacity = 1
        )
        val violations = validator.validate(request)
        assertTrue(violations.isEmpty())
    }
    
    @Test
    fun `should reject empty branchId`() {
        val request = CreateSlotRequest(
            branchId = "",
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        val violations = validator.validate(request)
        assertTrue(violations.any { it.message.contains("Branch ID") })
    }
    
    @Test
    fun `should reject capacity less than 1`() {
        val request = CreateSlotRequest(
            branchId = "branch1",
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1),
            capacity = 0
        )
        val violations = validator.validate(request)
        assertTrue(violations.any { it.message.contains("Capacity") })
    }
}
```

**Technical details:**
- Use Jakarta validation annotations for basic validation
- BulkCreateSlotsRequest limits to 100 slots per request to prevent abuse
- UpdateSlotRequest uses nullable fields to allow partial updates
- removeStaffAssignment flag explicitly handles staff unassignment

---

### Phase 2: Slot Validation Service
**Files**: 
- `src/main/kotlin/com/flowcare/backend/slot/service/SlotValidationService.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/slot/service/SlotValidationServiceTest.kt`

Implement business rule validation for slot operations.

**Key code changes:**
```kotlin
// SlotValidationService.kt
package com.flowcare.backend.slot.service

import com.flowcare.backend.branch.repository.BranchRepository
import com.flowcare.backend.service.repository.ServiceTypeRepository
import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.slot.dto.CreateSlotRequest
import com.flowcare.backend.slot.exception.SlotValidationException
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class SlotValidationService(
    private val branchRepository: BranchRepository,
    private val serviceTypeRepository: ServiceTypeRepository,
    private val userRepository: UserRepository
) {
    
    fun validateSlotCreation(request: CreateSlotRequest) {
        // Check if branch exists
        if (!branchRepository.existsById(request.branchId)) {
            throw SlotValidationException("Branch not found: ${request.branchId}")
        }
        
        // Check if service type exists
        if (!serviceTypeRepository.existsById(request.serviceTypeId)) {
            throw SlotValidationException("Service type not found: ${request.serviceTypeId}")
        }
        
        // Check if staff exists and belongs to branch (if provided)
        request.staffId?.let { staffId ->
            val staff = userRepository.findById(staffId)
                .orElseThrow { SlotValidationException("Staff not found: $staffId") }
            
            if (staff.branchId != request.branchId) {
                throw SlotValidationException("Staff does not belong to branch ${request.branchId}")
            }
        }
        
        // Validate time range
        if (request.startAt.isAfter(request.endAt) || request.startAt.isEqual(request.endAt)) {
            throw SlotValidationException("Start time must be before end time")
        }
        
        // Validate not in past
        if (request.startAt.isBefore(OffsetDateTime.now())) {
            throw SlotValidationException("Cannot create slot in the past")
        }
    }
    
    fun validateSlotCreationSafe(request: CreateSlotRequest): String? {
        return try {
            validateSlotCreation(request)
            null
        } catch (e: SlotValidationException) {
            e.message
        }
    }
}
```

**Test cases:**
```kotlin
// SlotValidationServiceTest.kt
@SpringBootTest
class SlotValidationServiceTest {
    
    @Autowired
    private lateinit var validationService: SlotValidationService
    
    @Autowired
    private lateinit var branchRepository: BranchRepository
    
    @Test
    fun `should pass validation for valid slot`() {
        val branch = branchRepository.save(Branch(...))
        val request = CreateSlotRequest(
            branchId = branch.id,
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        assertDoesNotThrow { validationService.validateSlotCreation(request) }
    }
    
    @Test
    fun `should reject non-existent branch`() {
        val request = CreateSlotRequest(
            branchId = "invalid",
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        assertThrows<SlotValidationException> {
            validationService.validateSlotCreation(request)
        }
    }
    
    @Test
    fun `should reject past slot time`() {
        val request = CreateSlotRequest(
            branchId = "branch1",
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().minusDays(1),
            endAt = OffsetDateTime.now().minusDays(1).plusHours(1)
        )
        assertThrows<SlotValidationException> {
            validationService.validateSlotCreation(request)
        }
    }
    
    @Test
    fun `should reject when start time after end time`() {
        val request = CreateSlotRequest(
            branchId = "branch1",
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1).plusHours(2),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        assertThrows<SlotValidationException> {
            validationService.validateSlotCreation(request)
        }
    }
}
```

**Technical details:**
- validateSlotCreationSafe returns error message instead of throwing for bulk operations
- Validates staff belongs to the same branch as the slot
- Prevents creating slots in the past
- Ensures logical time ranges

---

### Phase 3: Slot Management Service (Create & Bulk Create)
**Files**: 
- `src/main/kotlin/com/flowcare/backend/slot/service/SlotManagementService.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/slot/service/SlotManagementServiceTest.kt`

Implement slot creation with audit logging and cache invalidation.

**Key code changes:**
```kotlin
// SlotManagementService.kt
package com.flowcare.backend.slot.service

import com.flowcare.backend.audit.service.AuditLogService
import com.flowcare.backend.slot.dto.*
import com.flowcare.backend.slot.model.Slot
import com.flowcare.backend.slot.repository.SlotRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Service
class SlotManagementService(
    private val slotRepository: SlotRepository,
    private val slotValidationService: SlotValidationService,
    private val auditLogService: AuditLogService
) {
    
    @Transactional
    @CacheEvict(value = ["availableSlots"], allEntries = true)
    fun createSlot(request: CreateSlotRequest, actorId: String, actorRole: String): SlotResponse {
        slotValidationService.validateSlotCreation(request)
        
        val slot = Slot(
            id = "slot_${UUID.randomUUID()}",
            branchId = request.branchId,
            serviceTypeId = request.serviceTypeId,
            staffId = request.staffId,
            startAt = request.startAt,
            endAt = request.endAt,
            capacity = request.capacity,
            bookedCount = 0,
            isActive = true,
            deletedAt = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        val saved = slotRepository.save(slot)
        
        auditLogService.log(
            actorId = actorId,
            actorRole = actorRole,
            actionType = "SLOT_CREATED",
            entityType = "Slot",
            entityId = saved.id,
            metadata = mapOf(
                "branchId" to saved.branchId,
                "serviceTypeId" to saved.serviceTypeId,
                "staffId" to (saved.staffId ?: "unassigned"),
                "startAt" to saved.startAt.toString(),
                "endAt" to saved.endAt.toString(),
                "capacity" to saved.capacity
            )
        )
        
        return saved.toResponse()
    }
    
    @Transactional
    @CacheEvict(value = ["availableSlots"], allEntries = true)
    fun createSlotsBulk(
        request: BulkCreateSlotsRequest,
        actorId: String,
        actorRole: String
    ): BulkCreateSlotsResponse {
        val createdSlots = mutableListOf<SlotResponse>()
        val failures = mutableListOf<SlotCreationFailure>()
        
        request.slots.forEachIndexed { index, slotRequest ->
            val validationError = slotValidationService.validateSlotCreationSafe(slotRequest)
            
            if (validationError != null) {
                failures.add(SlotCreationFailure(index, slotRequest, validationError))
            } else {
                try {
                    val slot = Slot(
                        id = "slot_${UUID.randomUUID()}",
                        branchId = slotRequest.branchId,
                        serviceTypeId = slotRequest.serviceTypeId,
                        staffId = slotRequest.staffId,
                        startAt = slotRequest.startAt,
                        endAt = slotRequest.endAt,
                        capacity = slotRequest.capacity,
                        bookedCount = 0,
                        isActive = true,
                        deletedAt = null,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                    val saved = slotRepository.save(slot)
                    createdSlots.add(saved.toResponse())
                } catch (e: Exception) {
                    failures.add(SlotCreationFailure(index, slotRequest, e.message ?: "Unknown error"))
                }
            }
        }
        
        if (createdSlots.isNotEmpty()) {
            auditLogService.log(
                actorId = actorId,
                actorRole = actorRole,
                actionType = "SLOT_CREATED",
                entityType = "Slot",
                entityId = "bulk_operation",
                metadata = mapOf(
                    "bulkCreation" to true,
                    "totalRequested" to request.slots.size,
                    "successCount" to createdSlots.size,
                    "failureCount" to failures.size,
                    "slotIds" to createdSlots.map { it.id }
                )
            )
        }
        
        return BulkCreateSlotsResponse(
            successCount = createdSlots.size,
            failureCount = failures.size,
            createdSlots = createdSlots,
            failures = failures
        )
    }
    
    private fun Slot.toResponse() = SlotResponse(
        id = id,
        branchId = branchId,
        serviceTypeId = serviceTypeId,
        staffId = staffId,
        startAt = startAt,
        endAt = endAt,
        capacity = capacity,
        bookedCount = bookedCount,
        isActive = isActive,
        deletedAt = deletedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
```

**Test cases:**
```kotlin
// SlotManagementServiceTest.kt
@SpringBootTest
@Transactional
class SlotManagementServiceTest {
    
    @Autowired
    private lateinit var slotManagementService: SlotManagementService
    
    @Autowired
    private lateinit var slotRepository: SlotRepository
    
    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository
    
    @Test
    fun `should create single slot successfully`() {
        val request = CreateSlotRequest(
            branchId = "branch1",
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        
        val response = slotManagementService.createSlot(request, "admin1", "ADMIN")
        
        assertNotNull(response.id)
        assertEquals(request.branchId, response.branchId)
        assertEquals(0, response.bookedCount)
        
        val auditLogs = auditLogRepository.findAll()
        assertTrue(auditLogs.any { it.actionType == "SLOT_CREATED" && it.entityId == response.id })
    }
    
    @Test
    fun `should create multiple slots in bulk`() {
        val request = BulkCreateSlotsRequest(
            slots = listOf(
                CreateSlotRequest("branch1", "service1", null, 
                    OffsetDateTime.now().plusDays(1), 
                    OffsetDateTime.now().plusDays(1).plusHours(1)),
                CreateSlotRequest("branch1", "service1", null,
                    OffsetDateTime.now().plusDays(2),
                    OffsetDateTime.now().plusDays(2).plusHours(1))
            )
        )
        
        val response = slotManagementService.createSlotsBulk(request, "admin1", "ADMIN")
        
        assertEquals(2, response.successCount)
        assertEquals(0, response.failureCount)
        assertEquals(2, response.createdSlots.size)
    }
    
    @Test
    fun `should handle partial failures in bulk creation`() {
        val request = BulkCreateSlotsRequest(
            slots = listOf(
                CreateSlotRequest("branch1", "service1", null,
                    OffsetDateTime.now().plusDays(1),
                    OffsetDateTime.now().plusDays(1).plusHours(1)),
                CreateSlotRequest("invalid_branch", "service1", null,
                    OffsetDateTime.now().plusDays(1),
                    OffsetDateTime.now().plusDays(1).plusHours(1))
            )
        )
        
        val response = slotManagementService.createSlotsBulk(request, "admin1", "ADMIN")
        
        assertEquals(1, response.successCount)
        assertEquals(1, response.failureCount)
        assertEquals(1, response.failures.size)
        assertTrue(response.failures[0].reason.contains("Branch not found"))
    }
    
    @Test
    fun `should invalidate cache after slot creation`() {
        // This would require mocking CacheManager to verify cache eviction
        // For integration test, verify that subsequent queries return updated data
        val request = CreateSlotRequest(
            branchId = "branch1",
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        
        slotManagementService.createSlot(request, "admin1", "ADMIN")
        
        val slots = slotRepository.findAvailableSlots("branch1", "service1", OffsetDateTime.now())
        assertTrue(slots.isNotEmpty())
    }
}
```

**Technical details:**
- @CacheEvict clears all cached available slots after modifications
- Bulk creation continues on errors, collecting failures
- Single audit log for bulk operations with array of slot IDs
- Uses UUID for slot IDs with "slot_" prefix

---

### Phase 4: Slot Update and Soft Delete
**Files**: 
- `src/main/kotlin/com/flowcare/backend/slot/service/SlotManagementService.kt` (extend)
- `src/main/kotlin/com/flowcare/backend/slot/exception/SlotAlreadyDeletedException.kt`
- `src/main/kotlin/com/flowcare/backend/slot/exception/SlotHasActiveBookingsException.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/slot/service/SlotManagementServiceTest.kt` (extend)

Add update and soft-delete operations with booking validation.

**Key code changes:**
```kotlin
// SlotManagementService.kt (add methods)
@Transactional
@CacheEvict(value = ["availableSlots"], allEntries = true)
fun updateSlot(
    slotId: String,
    request: UpdateSlotRequest,
    actorId: String,
    actorRole: String
): SlotResponse {
    val slot = slotRepository.findById(slotId)
        .orElseThrow { SlotValidationException("Slot not found: $slotId") }
    
    if (slot.deletedAt != null) {
        throw SlotAlreadyDeletedException("Cannot update deleted slot: $slotId")
    }
    
    val oldValues = mutableMapOf<String, Any?>()
    val newValues = mutableMapOf<String, Any?>()
    
    // For booked slots, only allow staff assignment changes
    val isBooked = slot.bookedCount > 0
    
    if (isBooked) {
        // Only allow staff changes for booked slots
        if (request.startAt != null || request.endAt != null || request.capacity != null) {
            throw SlotValidationException("Cannot change time or capacity of booked slot")
        }
    }
    
    // Update fields
    val updatedSlot = slot.copy(
        startAt = if (!isBooked && request.startAt != null) {
            oldValues["startAt"] = slot.startAt.toString()
            newValues["startAt"] = request.startAt.toString()
            request.startAt
        } else slot.startAt,
        
        endAt = if (!isBooked && request.endAt != null) {
            oldValues["endAt"] = slot.endAt.toString()
            newValues["endAt"] = request.endAt.toString()
            request.endAt
        } else slot.endAt,
        
        staffId = when {
            request.removeStaffAssignment -> {
                oldValues["staffId"] = slot.staffId ?: "unassigned"
                newValues["staffId"] = "unassigned"
                null
            }
            request.staffId != null -> {
                oldValues["staffId"] = slot.staffId ?: "unassigned"
                newValues["staffId"] = request.staffId
                request.staffId
            }
            else -> slot.staffId
        },
        
        capacity = if (!isBooked && request.capacity != null) {
            if (request.capacity < slot.bookedCount) {
                throw SlotValidationException("Cannot reduce capacity below booked count")
            }
            oldValues["capacity"] = slot.capacity
            newValues["capacity"] = request.capacity
            request.capacity
        } else slot.capacity,
        
        updatedAt = LocalDateTime.now()
    )
    
    val saved = slotRepository.save(updatedSlot)
    
    if (oldValues.isNotEmpty()) {
        auditLogService.log(
            actorId = actorId,
            actorRole = actorRole,
            actionType = "SLOT_UPDATED",
            entityType = "Slot",
            entityId = saved.id,
            metadata = mapOf(
                "branchId" to saved.branchId,
                "oldValues" to oldValues,
                "newValues" to newValues
            )
        )
    }
    
    return saved.toResponse()
}

@Transactional
@CacheEvict(value = ["availableSlots"], allEntries = true)
fun softDeleteSlot(slotId: String, actorId: String, actorRole: String): SlotResponse {
    val slot = slotRepository.findById(slotId)
        .orElseThrow { SlotValidationException("Slot not found: $slotId") }
    
    if (slot.deletedAt != null) {
        throw SlotAlreadyDeletedException("Slot already deleted: $slotId")
    }
    
    // Check for active bookings (booked count > 0 means active bookings exist)
    if (slot.bookedCount > 0) {
        throw SlotHasActiveBookingsException("Cannot delete slot with active bookings: $slotId")
    }
    
    val deletedSlot = slot.copy(
        deletedAt = OffsetDateTime.now(),
        updatedAt = LocalDateTime.now()
    )
    
    val saved = slotRepository.save(deletedSlot)
    
    auditLogService.log(
        actorId = actorId,
        actorRole = actorRole,
        actionType = "SLOT_SOFT_DELETED",
        entityType = "Slot",
        entityId = saved.id,
        metadata = mapOf(
            "branchId" to saved.branchId,
            "serviceTypeId" to saved.serviceTypeId,
            "staffId" to (saved.staffId ?: "unassigned"),
            "deletedAt" to saved.deletedAt.toString()
        )
    )
    
    return saved.toResponse()
}

fun getSlot(slotId: String, includeDeleted: Boolean = false): SlotResponse {
    val slot = slotRepository.findById(slotId)
        .orElseThrow { SlotValidationException("Slot not found: $slotId") }
    
    if (!includeDeleted && slot.deletedAt != null) {
        throw SlotValidationException("Slot not found: $slotId")
    }
    
    return slot.toResponse()
}

fun listSlots(
    branchId: String?,
    serviceTypeId: String?,
    includeDeleted: Boolean = false
): List<SlotResponse> {
    val slots = when {
        branchId != null && serviceTypeId != null -> 
            slotRepository.findByBranchIdAndServiceTypeId(branchId, serviceTypeId)
        branchId != null -> 
            slotRepository.findByBranchId(branchId)
        else -> 
            slotRepository.findAll()
    }
    
    return slots
        .filter { includeDeleted || it.deletedAt == null }
        .map { it.toResponse() }
}

// SlotAlreadyDeletedException.kt
class SlotAlreadyDeletedException(message: String) : RuntimeException(message)

// SlotHasActiveBookingsException.kt
class SlotHasActiveBookingsException(message: String) : RuntimeException(message)
```

**Test cases:**
```kotlin
// SlotManagementServiceTest.kt (add tests)
@Test
fun `should update slot staff assignment`() {
    val slot = createTestSlot()
    val request = UpdateSlotRequest(staffId = "staff123")
    
    val response = slotManagementService.updateSlot(slot.id, request, "admin1", "ADMIN")
    
    assertEquals("staff123", response.staffId)
    
    val auditLogs = auditLogRepository.findAll()
    assertTrue(auditLogs.any { 
        it.actionType == "SLOT_UPDATED" && it.entityId == slot.id 
    })
}

@Test
fun `should remove staff assignment from slot`() {
    val slot = createTestSlot(staffId = "staff123")
    val request = UpdateSlotRequest(removeStaffAssignment = true)
    
    val response = slotManagementService.updateSlot(slot.id, request, "admin1", "ADMIN")
    
    assertNull(response.staffId)
}

@Test
fun `should update slot time for unbooked slot`() {
    val slot = createTestSlot()
    val newStartAt = OffsetDateTime.now().plusDays(2)
    val request = UpdateSlotRequest(startAt = newStartAt)
    
    val response = slotManagementService.updateSlot(slot.id, request, "admin1", "ADMIN")
    
    assertEquals(newStartAt, response.startAt)
}

@Test
fun `should reject time update for booked slot`() {
    val slot = createTestSlot(bookedCount = 1)
    val request = UpdateSlotRequest(startAt = OffsetDateTime.now().plusDays(2))
    
    assertThrows<SlotValidationException> {
        slotManagementService.updateSlot(slot.id, request, "admin1", "ADMIN")
    }
}

@Test
fun `should allow staff update for booked slot`() {
    val slot = createTestSlot(bookedCount = 1)
    val request = UpdateSlotRequest(staffId = "staff456")
    
    val response = slotManagementService.updateSlot(slot.id, request, "admin1", "ADMIN")
    
    assertEquals("staff456", response.staffId)
    assertEquals(1, response.bookedCount)
}

@Test
fun `should reject update of deleted slot`() {
    val slot = createTestSlot(deletedAt = OffsetDateTime.now())
    val request = UpdateSlotRequest(staffId = "staff123")
    
    assertThrows<SlotAlreadyDeletedException> {
        slotManagementService.updateSlot(slot.id, request, "admin1", "ADMIN")
    }
}

@Test
fun `should soft delete unbooked slot`() {
    val slot = createTestSlot()
    
    val response = slotManagementService.softDeleteSlot(slot.id, "admin1", "ADMIN")
    
    assertNotNull(response.deletedAt)
    
    val auditLogs = auditLogRepository.findAll()
    assertTrue(auditLogs.any { 
        it.actionType == "SLOT_SOFT_DELETED" && it.entityId == slot.id 
    })
}

@Test
fun `should reject soft delete of booked slot`() {
    val slot = createTestSlot(bookedCount = 1)
    
    assertThrows<SlotHasActiveBookingsException> {
        slotManagementService.softDeleteSlot(slot.id, "admin1", "ADMIN")
    }
}

@Test
fun `should reject soft delete of already deleted slot`() {
    val slot = createTestSlot(deletedAt = OffsetDateTime.now())
    
    assertThrows<SlotAlreadyDeletedException> {
        slotManagementService.softDeleteSlot(slot.id, "admin1", "ADMIN")
    }
}

@Test
fun `should list slots excluding deleted by default`() {
    createTestSlot()
    createTestSlot(deletedAt = OffsetDateTime.now())
    
    val slots = slotManagementService.listSlots("branch1", "service1", includeDeleted = false)
    
    assertEquals(1, slots.size)
    assertTrue(slots.all { it.deletedAt == null })
}

@Test
fun `should list slots including deleted when requested`() {
    createTestSlot()
    createTestSlot(deletedAt = OffsetDateTime.now())
    
    val slots = slotManagementService.listSlots("branch1", "service1", includeDeleted = true)
    
    assertEquals(2, slots.size)
}

private fun createTestSlot(
    staffId: String? = null,
    bookedCount: Int = 0,
    deletedAt: OffsetDateTime? = null
): Slot {
    val slot = Slot(
        id = "slot_${UUID.randomUUID()}",
        branchId = "branch1",
        serviceTypeId = "service1",
        staffId = staffId,
        startAt = OffsetDateTime.now().plusDays(1),
        endAt = OffsetDateTime.now().plusDays(1).plusHours(1),
        capacity = 1,
        bookedCount = bookedCount,
        isActive = true,
        deletedAt = deletedAt,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )
    return slotRepository.save(slot)
}
```

**Technical details:**
- Booked slots can only have staff assignment changed, not time/capacity
- Capacity cannot be reduced below current booked count
- Soft delete checks bookedCount > 0 for active bookings
- Audit logs track old and new values for updates

---

### Phase 5: REST API Controllers with Authorization
**Files**: 
- `src/main/kotlin/com/flowcare/backend/slot/controller/SlotManagementController.kt`
- `src/main/kotlin/com/flowcare/backend/common/exception/GlobalExceptionHandler.kt` (extend)

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/slot/controller/SlotManagementControllerTest.kt`

Create REST endpoints with role-based access control.

**Key code changes:**
```kotlin
// SlotManagementController.kt
package com.flowcare.backend.slot.controller

import com.flowcare.backend.auth.service.BranchAccessService
import com.flowcare.backend.slot.dto.*
import com.flowcare.backend.slot.service.SlotManagementService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/slots")
class SlotManagementController(
    private val slotManagementService: SlotManagementService,
    private val branchAccessService: BranchAccessService
) {
    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun createSlot(@Valid @RequestBody request: CreateSlotRequest): ResponseEntity<SlotResponse> {
        validateBranchAccess(request.branchId)
        
        val actorId = branchAccessService.getCurrentUserId()!!
        val actorRole = getCurrentRole()
        
        val response = slotManagementService.createSlot(request, actorId, actorRole)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
    
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun createSlotsBulk(
        @Valid @RequestBody request: BulkCreateSlotsRequest
    ): ResponseEntity<BulkCreateSlotsResponse> {
        // Validate branch access for all slots
        request.slots.forEach { validateBranchAccess(it.branchId) }
        
        val actorId = branchAccessService.getCurrentUserId()!!
        val actorRole = getCurrentRole()
        
        val response = slotManagementService.createSlotsBulk(request, actorId, actorRole)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
    
    @PatchMapping("/{slotId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun updateSlot(
        @PathVariable slotId: String,
        @Valid @RequestBody request: UpdateSlotRequest
    ): ResponseEntity<SlotResponse> {
        val slot = slotManagementService.getSlot(slotId)
        validateBranchAccess(slot.branchId)
        
        val actorId = branchAccessService.getCurrentUserId()!!
        val actorRole = getCurrentRole()
        
        val response = slotManagementService.updateSlot(slotId, request, actorId, actorRole)
        return ResponseEntity.ok(response)
    }
    
    @DeleteMapping("/{slotId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun softDeleteSlot(@PathVariable slotId: String): ResponseEntity<SlotResponse> {
        val slot = slotManagementService.getSlot(slotId)
        validateBranchAccess(slot.branchId)
        
        val actorId = branchAccessService.getCurrentUserId()!!
        val actorRole = getCurrentRole()
        
        val response = slotManagementService.softDeleteSlot(slotId, actorId, actorRole)
        return ResponseEntity.ok(response)
    }
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun listSlots(
        @RequestParam(required = false) branchId: String?,
        @RequestParam(required = false) serviceTypeId: String?,
        @RequestParam(required = false, defaultValue = "false") includeDeleted: Boolean
    ): ResponseEntity<List<SlotResponse>> {
        // Branch managers can only see their branch
        val effectiveBranchId = if (branchAccessService.isBranchManager()) {
            branchAccessService.getCurrentUserBranchId()
        } else {
            branchId
        }
        
        // Only admins can see deleted slots
        val effectiveIncludeDeleted = includeDeleted && branchAccessService.isAdmin()
        
        val slots = slotManagementService.listSlots(
            effectiveBranchId,
            serviceTypeId,
            effectiveIncludeDeleted
        )
        return ResponseEntity.ok(slots)
    }
    
    @GetMapping("/{slotId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun getSlot(
        @PathVariable slotId: String,
        @RequestParam(required = false, defaultValue = "false") includeDeleted: Boolean
    ): ResponseEntity<SlotResponse> {
        val effectiveIncludeDeleted = includeDeleted && branchAccessService.isAdmin()
        val slot = slotManagementService.getSlot(slotId, effectiveIncludeDeleted)
        validateBranchAccess(slot.branchId)
        return ResponseEntity.ok(slot)
    }
    
    private fun validateBranchAccess(branchId: String) {
        if (!branchAccessService.hasAccessToBranch(branchId)) {
            throw org.springframework.security.access.AccessDeniedException(
                "You do not have access to branch: $branchId"
            )
        }
    }
    
    private fun getCurrentRole(): String {
        return when {
            branchAccessService.isAdmin() -> "ADMIN"
            branchAccessService.isBranchManager() -> "BRANCH_MANAGER"
            else -> "UNKNOWN"
        }
    }
}

// GlobalExceptionHandler.kt (add handlers)
@ExceptionHandler(SlotValidationException::class)
fun handleSlotValidationException(ex: SlotValidationException): ResponseEntity<ErrorResponse> {
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse(ex.message ?: "Validation error"))
}

@ExceptionHandler(SlotAlreadyDeletedException::class)
fun handleSlotAlreadyDeletedException(ex: SlotAlreadyDeletedException): ResponseEntity<ErrorResponse> {
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(ErrorResponse(ex.message ?: "Slot already deleted"))
}

@ExceptionHandler(SlotHasActiveBookingsException::class)
fun handleSlotHasActiveBookingsException(ex: SlotHasActiveBookingsException): ResponseEntity<ErrorResponse> {
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(ErrorResponse(ex.message ?: "Slot has active bookings"))
}
```

**Test cases:**
```kotlin
// SlotManagementControllerTest.kt
@SpringBootTest
@AutoConfigureMockMvc
class SlotManagementControllerTest {
    
    @Autowired
    private lateinit var mockMvc: MockMvc
    
    @Autowired
    private lateinit var objectMapper: ObjectMapper
    
    @Autowired
    private lateinit var userRepository: UserRepository
    
    @Autowired
    private lateinit var slotRepository: SlotRepository
    
    private lateinit var admin: User
    private lateinit var branchManager: User
    private lateinit var staff: User
    
    @BeforeEach
    fun setup() {
        slotRepository.deleteAll()
        userRepository.deleteAll()
        
        admin = userRepository.save(User(
            id = "admin1",
            username = "admin",
            password = "password",
            role = Role.ADMIN,
            fullName = "Admin User",
            email = "admin@test.com"
        ))
        
        branchManager = userRepository.save(User(
            id = "manager1",
            username = "manager",
            password = "password",
            role = Role.BRANCH_MANAGER,
            fullName = "Manager User",
            email = "manager@test.com",
            branchId = "branch1"
        ))
        
        staff = userRepository.save(User(
            id = "staff1",
            username = "staff",
            password = "password",
            role = Role.STAFF,
            fullName = "Staff User",
            email = "staff@test.com",
            branchId = "branch1"
        ))
    }
    
    @Test
    fun `admin should create slot successfully`() {
        val request = CreateSlotRequest(
            branchId = "branch1",
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        
        mockMvc.perform(post("/api/slots")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.branchId").value("branch1"))
    }
    
    @Test
    fun `branch manager should create slot for own branch`() {
        val request = CreateSlotRequest(
            branchId = "branch1",
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        
        mockMvc.perform(post("/api/slots")
            .with(httpBasic("manager", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated)
    }
    
    @Test
    fun `branch manager should not create slot for other branch`() {
        val request = CreateSlotRequest(
            branchId = "branch2",
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        
        mockMvc.perform(post("/api/slots")
            .with(httpBasic("manager", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden)
    }
    
    @Test
    fun `staff should not create slots`() {
        val request = CreateSlotRequest(
            branchId = "branch1",
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        
        mockMvc.perform(post("/api/slots")
            .with(httpBasic("staff", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden)
    }
    
    @Test
    fun `should create multiple slots in bulk`() {
        val request = BulkCreateSlotsRequest(
            slots = listOf(
                CreateSlotRequest("branch1", "service1", null,
                    OffsetDateTime.now().plusDays(1),
                    OffsetDateTime.now().plusDays(1).plusHours(1)),
                CreateSlotRequest("branch1", "service1", null,
                    OffsetDateTime.now().plusDays(2),
                    OffsetDateTime.now().plusDays(2).plusHours(1))
            )
        )
        
        mockMvc.perform(post("/api/slots/bulk")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.successCount").value(2))
            .andExpect(jsonPath("$.failureCount").value(0))
    }
    
    @Test
    fun `should update slot successfully`() {
        val slot = createTestSlot()
        val request = UpdateSlotRequest(staffId = "staff1")
        
        mockMvc.perform(patch("/api/slots/${slot.id}")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.staffId").value("staff1"))
    }
    
    @Test
    fun `should soft delete slot successfully`() {
        val slot = createTestSlot()
        
        mockMvc.perform(delete("/api/slots/${slot.id}")
            .with(httpBasic("admin", "password")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deletedAt").exists())
    }
    
    @Test
    fun `should list slots for branch manager`() {
        createTestSlot()
        
        mockMvc.perform(get("/api/slots")
            .with(httpBasic("manager", "password"))
            .param("branchId", "branch1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }
    
    @Test
    fun `admin should see deleted slots when requested`() {
        val slot = createTestSlot()
        slotRepository.save(slot.copy(deletedAt = OffsetDateTime.now()))
        
        mockMvc.perform(get("/api/slots")
            .with(httpBasic("admin", "password"))
            .param("includeDeleted", "true"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.deletedAt)]").exists())
    }
    
    @Test
    fun `branch manager should not see deleted slots`() {
        val slot = createTestSlot()
        slotRepository.save(slot.copy(deletedAt = OffsetDateTime.now()))
        
        mockMvc.perform(get("/api/slots")
            .with(httpBasic("manager", "password"))
            .param("includeDeleted", "true"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.deletedAt)]").doesNotExist())
    }
    
    private fun createTestSlot(): Slot {
        return slotRepository.save(Slot(
            id = "slot_${UUID.randomUUID()}",
            branchId = "branch1",
            serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1),
            capacity = 1,
            bookedCount = 0,
            isActive = true,
            deletedAt = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        ))
    }
}
```

**Technical details:**
- Branch managers automatically filtered to their branch in list endpoint
- Only admins can view deleted slots via includeDeleted parameter
- Authorization checked before operations using BranchAccessService
- Follows existing pattern from AppointmentController

---

### Phase 6: Repository Extensions
**Files**: 
- `src/main/kotlin/com/flowcare/backend/slot/repository/SlotRepository.kt` (extend)

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/slot/repository/SlotRepositoryTest.kt`

Add repository methods for slot management queries.

**Key code changes:**
```kotlin
// SlotRepository.kt (add methods)
package com.flowcare.backend.slot.repository

import com.flowcare.backend.slot.model.Slot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface SlotRepository : JpaRepository<Slot, String> {
    
    // Existing methods...
    @Query("""
        SELECT s FROM Slot s 
        WHERE s.branchId = :branchId 
        AND s.serviceTypeId = :serviceTypeId 
        AND s.deletedAt IS NULL 
        AND s.isActive = true 
        AND s.startAt > :now 
        AND s.bookedCount < s.capacity
        ORDER BY s.startAt ASC
    """)
    fun findAvailableSlots(
        @Param("branchId") branchId: String,
        @Param("serviceTypeId") serviceTypeId: String,
        @Param("now") now: OffsetDateTime
    ): List<Slot>
    
    @Query("""
        SELECT s FROM Slot s 
        WHERE s.branchId = :branchId 
        AND s.serviceTypeId = :serviceTypeId 
        AND s.deletedAt IS NULL 
        AND s.isActive = true 
        AND s.startAt > :now 
        AND s.bookedCount < s.capacity
        AND DATE(s.startAt) = DATE(:date)
        ORDER BY s.startAt ASC
    """)
    fun findAvailableSlotsByDate(
        @Param("branchId") branchId: String,
        @Param("serviceTypeId") serviceTypeId: String,
        @Param("date") date: OffsetDateTime,
        @Param("now") now: OffsetDateTime
    ): List<Slot>
    
    // New methods for slot management
    fun findByBranchId(branchId: String): List<Slot>
    
    fun findByBranchIdAndServiceTypeId(branchId: String, serviceTypeId: String): List<Slot>
    
    @Query("""
        SELECT s FROM Slot s 
        WHERE s.branchId = :branchId 
        AND s.deletedAt IS NULL
        ORDER BY s.startAt ASC
    """)
    fun findActiveByBranchId(@Param("branchId") branchId: String): List<Slot>
    
    @Query("""
        SELECT s FROM Slot s 
        WHERE s.deletedAt IS NOT NULL
        AND s.deletedAt < :cutoffDate
    """)
    fun findSoftDeletedBefore(@Param("cutoffDate") cutoffDate: OffsetDateTime): List<Slot>
}
```

**Test cases:**
```kotlin
// SlotRepositoryTest.kt
@SpringBootTest
@Transactional
class SlotRepositoryTest {
    
    @Autowired
    private lateinit var slotRepository: SlotRepository
    
    @Test
    fun `should find slots by branch ID`() {
        slotRepository.save(createSlot("branch1", "service1"))
        slotRepository.save(createSlot("branch1", "service2"))
        slotRepository.save(createSlot("branch2", "service1"))
        
        val slots = slotRepository.findByBranchId("branch1")
        
        assertEquals(2, slots.size)
        assertTrue(slots.all { it.branchId == "branch1" })
    }
    
    @Test
    fun `should find slots by branch and service type`() {
        slotRepository.save(createSlot("branch1", "service1"))
        slotRepository.save(createSlot("branch1", "service2"))
        
        val slots = slotRepository.findByBranchIdAndServiceTypeId("branch1", "service1")
        
        assertEquals(1, slots.size)
        assertEquals("service1", slots[0].serviceTypeId)
    }
    
    @Test
    fun `should find active slots excluding deleted`() {
        slotRepository.save(createSlot("branch1", "service1"))
        slotRepository.save(createSlot("branch1", "service1", deletedAt = OffsetDateTime.now()))
        
        val slots = slotRepository.findActiveByBranchId("branch1")
        
        assertEquals(1, slots.size)
        assertNull(slots[0].deletedAt)
    }
    
    @Test
    fun `should find soft deleted slots before cutoff date`() {
        val oldDeleted = OffsetDateTime.now().minusDays(40)
        val recentDeleted = OffsetDateTime.now().minusDays(10)
        
        slotRepository.save(createSlot("branch1", "service1", deletedAt = oldDeleted))
        slotRepository.save(createSlot("branch1", "service1", deletedAt = recentDeleted))
        slotRepository.save(createSlot("branch1", "service1"))
        
        val cutoff = OffsetDateTime.now().minusDays(30)
        val slots = slotRepository.findSoftDeletedBefore(cutoff)
        
        assertEquals(1, slots.size)
        assertTrue(slots[0].deletedAt!!.isBefore(cutoff))
    }
    
    private fun createSlot(
        branchId: String,
        serviceTypeId: String,
        deletedAt: OffsetDateTime? = null
    ): Slot {
        return Slot(
            id = "slot_${UUID.randomUUID()}",
            branchId = branchId,
            serviceTypeId = serviceTypeId,
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1),
            capacity = 1,
            bookedCount = 0,
            isActive = true,
            deletedAt = deletedAt,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
}
```

**Technical details:**
- findSoftDeletedBefore used for cleanup operations (Story 18)
- findActiveByBranchId excludes soft-deleted slots
- Existing available slots queries remain unchanged

---

## Technical Considerations

**Dependencies:**
- No new packages required
- Uses existing Spring Security, JPA, and validation infrastructure
- Leverages existing AuditLogService and BranchAccessService

**Edge Cases:**
- Bulk creation with mixed valid/invalid slots - handled with partial success response
- Updating booked slots - restricted to staff assignment only
- Deleting slots with cancelled appointments - allowed (bookedCount check)
- Concurrent slot updates - handled by @Version optimistic locking in Slot entity
- Cache consistency - @CacheEvict ensures immediate visibility

**Testing Strategy:**
- Unit tests for validation logic in SlotValidationService
- Service layer tests for business logic with mocked repositories
- Integration tests for controllers with full Spring context
- Repository tests for custom queries
- Test coverage for authorization (admin vs branch manager access)

**Performance:**
- Cache invalidation clears all entries - acceptable for MVP, can optimize later with selective eviction
- Bulk operations limited to 100 slots to prevent memory issues
- Database indexes on branchId, serviceTypeId, deletedAt recommended

**Security:**
- Role-based access control via @PreAuthorize
- Branch-level isolation for branch managers
- Deleted slots only visible to admins
- All operations require authentication

---

## Success Criteria

- [ ] Story 11 AC #1-11: Admins and Branch Managers can create single and bulk slots with validation and audit logging
- [ ] Story 12 AC #1-11: Admins and Branch Managers can update slots with restrictions for booked slots
- [ ] Story 13 AC #1-11: Admins and Branch Managers can soft-delete unbooked slots, admins can view deleted slots
- [ ] All endpoints enforce role-based access control
- [ ] Cache invalidation works correctly after slot modifications
- [ ] Audit logs created for all slot operations (create, update, delete)
- [ ] Bulk operations handle partial failures gracefully
- [ ] Integration tests pass for all authorization scenarios
- [ ] Repository queries perform efficiently with proper filtering
