# Administrative & Compliance (Stories 17-20) Implementation Plan

## Overview
Implement customer information viewing with ID document access, soft-delete cleanup with configurable retention, audit log viewing and CSV export, and staff listing with role-based access control.

## Architecture
Admin/Manager view customers with pagination → Admin downloads ID documents (audit logged) → Admin configures retention period in database → Admin triggers cleanup to hard-delete expired soft-deleted slots → Admin/Manager view audit logs with date filtering → Admin exports audit logs as CSV → Admin/Manager list staff with pagination and service assignments

## Implementation Phases

### Phase 1: System Configuration Model (Story 18 Foundation)
**Files**: 
- `src/main/kotlin/com/flowcare/backend/config/model/SystemConfig.kt`
- `src/main/kotlin/com/flowcare/backend/config/repository/SystemConfigRepository.kt`
- `src/test/kotlin/com/flowcare/backend/config/repository/SystemConfigRepositoryTest.kt`

Create system configuration table to store retention period and other system-wide settings.

**Key code changes:**
```kotlin
// config/model/SystemConfig.kt
package com.flowcare.backend.config.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "system_config")
data class SystemConfig(
    @Id
    val configKey: String,
    
    @Column(nullable = false)
    val configValue: String,
    
    @Column(columnDefinition = "TEXT")
    val description: String? = null,
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

// config/repository/SystemConfigRepository.kt
package com.flowcare.backend.config.repository

import com.flowcare.backend.config.model.SystemConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SystemConfigRepository : JpaRepository<SystemConfig, String>
```

**Test cases:**
```kotlin
// test/kotlin/com/flowcare/backend/config/repository/SystemConfigRepositoryTest.kt
package com.flowcare.backend.config.repository

import com.flowcare.backend.config.model.SystemConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
class SystemConfigRepositoryTest {

    @Autowired
    private lateinit var repository: SystemConfigRepository

    @Test
    fun `should save and retrieve system config`() {
        val config = SystemConfig(
            configKey = "RETENTION_PERIOD_DAYS",
            configValue = "30",
            description = "Soft-delete retention period in days"
        )
        
        repository.save(config)
        val found = repository.findById("RETENTION_PERIOD_DAYS")
        
        assertTrue(found.isPresent)
        assertEquals("30", found.get().configValue)
    }

    @Test
    fun `should update existing config value`() {
        val config = SystemConfig(configKey = "TEST_KEY", configValue = "10")
        repository.save(config)
        
        val updated = config.copy(configValue = "20")
        repository.save(updated)
        
        val found = repository.findById("TEST_KEY")
        assertEquals("20", found.get().configValue)
    }
}
```

**Technical details:**
- Use configKey as primary key for simple key-value storage
- Default retention period: 30 days (seeded in database)
- updatedAt tracks when configuration was last changed

---

### Phase 2: Customer Information Viewing (Story 17)
**Files**: 
- `src/main/kotlin/com/flowcare/backend/auth/controller/CustomerController.kt`
- `src/main/kotlin/com/flowcare/backend/auth/service/CustomerService.kt`
- `src/main/kotlin/com/flowcare/backend/auth/dto/CustomerListResponse.kt`
- `src/main/kotlin/com/flowcare/backend/auth/dto/CustomerDetailResponse.kt`
- `src/test/kotlin/com/flowcare/backend/auth/controller/CustomerControllerTest.kt`
- `src/test/kotlin/com/flowcare/backend/auth/service/CustomerServiceTest.kt`

Admin views all customers, Branch Manager views customers with appointments at their branch, with pagination.

**Key code changes:**
```kotlin
// auth/dto/CustomerListResponse.kt
package com.flowcare.backend.auth.dto

import java.time.LocalDateTime

data class CustomerListResponse(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val registeredAt: LocalDateTime
)

data class CustomerDetailResponse(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val idDocumentPath: String?,
    val registeredAt: LocalDateTime,
    val appointmentCount: Int
)

// auth/service/CustomerService.kt
package com.flowcare.backend.auth.service

import com.flowcare.backend.auth.dto.CustomerDetailResponse
import com.flowcare.backend.auth.dto.CustomerListResponse
import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.appointment.repository.AppointmentRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class CustomerService(
    private val userRepository: UserRepository,
    private val appointmentRepository: AppointmentRepository
) {
    
    fun listCustomers(pageable: Pageable): Page<CustomerListResponse> {
        return userRepository.findByRole(Role.CUSTOMER, pageable)
            .map { user ->
                CustomerListResponse(
                    id = user.id,
                    fullName = user.fullName,
                    email = user.email,
                    phone = user.phone,
                    registeredAt = user.createdAt
                )
            }
    }
    
    fun listCustomersByBranch(branchId: String, pageable: Pageable): Page<CustomerListResponse> {
        return userRepository.findCustomersWithAppointmentsAtBranch(branchId, pageable)
            .map { user ->
                CustomerListResponse(
                    id = user.id,
                    fullName = user.fullName,
                    email = user.email,
                    phone = user.phone,
                    registeredAt = user.createdAt
                )
            }
    }
    
    fun getCustomerDetail(customerId: String): CustomerDetailResponse {
        val user = userRepository.findById(customerId)
            .orElseThrow { IllegalArgumentException("Customer not found") }
        
        if (user.role != Role.CUSTOMER) {
            throw IllegalArgumentException("User is not a customer")
        }
        
        val appointmentCount = appointmentRepository.countByCustomerId(customerId)
        
        return CustomerDetailResponse(
            id = user.id,
            fullName = user.fullName,
            email = user.email,
            phone = user.phone,
            idDocumentPath = user.idDocumentPath,
            registeredAt = user.createdAt,
            appointmentCount = appointmentCount
        )
    }
}

// auth/controller/CustomerController.kt
package com.flowcare.backend.auth.controller

import com.flowcare.backend.auth.dto.CustomerDetailResponse
import com.flowcare.backend.auth.dto.CustomerListResponse
import com.flowcare.backend.auth.service.BranchAccessService
import com.flowcare.backend.auth.service.CustomerService
import com.flowcare.backend.audit.service.AuditLogService
import com.flowcare.backend.storage.service.FileStorageService
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.nio.file.Files

@RestController
@RequestMapping("/api/customers")
class CustomerController(
    private val customerService: CustomerService,
    private val branchAccessService: BranchAccessService,
    private val fileStorageService: FileStorageService,
    private val auditLogService: AuditLogService
) {
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun listCustomers(
        @AuthenticationPrincipal userDetails: UserDetails,
        pageable: Pageable
    ): Page<CustomerListResponse> {
        val user = branchAccessService.getCurrentUser(userDetails.username)
        
        return if (branchAccessService.isAdmin(user)) {
            customerService.listCustomers(pageable)
        } else {
            val branchId = branchAccessService.getUserBranchId(user)
            customerService.listCustomersByBranch(branchId, pageable)
        }
    }
    
    @GetMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun getCustomerDetail(
        @PathVariable customerId: String
    ): CustomerDetailResponse {
        return customerService.getCustomerDetail(customerId)
    }
    
    @GetMapping("/{customerId}/id-document")
    @PreAuthorize("hasRole('ADMIN')")
    fun downloadIdDocument(
        @PathVariable customerId: String,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<Resource> {
        val user = branchAccessService.getCurrentUser(userDetails.username)
        val customerDetail = customerService.getCustomerDetail(customerId)
        
        if (customerDetail.idDocumentPath == null) {
            return ResponseEntity.notFound().build()
        }
        
        val filePath = fileStorageService.loadFile(customerDetail.idDocumentPath)
        
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build()
        }
        
        // Audit log ID document access
        auditLogService.log(
            actorId = user.id,
            actorRole = user.role.name,
            actionType = "ID_DOCUMENT_ACCESSED",
            entityType = "Customer",
            entityId = customerId,
            metadata = mapOf(
                "documentPath" to customerDetail.idDocumentPath,
                "customerName" to customerDetail.fullName
            )
        )
        
        val resource = UrlResource(filePath.toUri())
        val contentType = Files.probeContentType(filePath) ?: "application/octet-stream"
        
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${filePath.fileName}\"")
            .body(resource)
    }
}
```

**Test cases:**
```kotlin
// test/kotlin/com/flowcare/backend/auth/service/CustomerServiceTest.kt
package com.flowcare.backend.auth.service

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.appointment.repository.AppointmentRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.*
import kotlin.test.assertEquals

class CustomerServiceTest {

    private val userRepository: UserRepository = mock()
    private val appointmentRepository: AppointmentRepository = mock()
    private val service = CustomerService(userRepository, appointmentRepository)

    @Test
    fun `should list all customers with pagination`() {
        val pageable = PageRequest.of(0, 10)
        val customers = listOf(
            User(id = "c1", username = "john", password = "pass", role = Role.CUSTOMER, 
                fullName = "John Doe", email = "john@test.com")
        )
        whenever(userRepository.findByRole(Role.CUSTOMER, pageable))
            .thenReturn(PageImpl(customers))
        
        val result = service.listCustomers(pageable)
        
        assertEquals(1, result.content.size)
        assertEquals("John Doe", result.content[0].fullName)
    }

    @Test
    fun `should get customer detail with appointment count`() {
        val customer = User(id = "c1", username = "john", password = "pass", role = Role.CUSTOMER,
            fullName = "John Doe", email = "john@test.com", idDocumentPath = "id_documents/doc.jpg")
        
        whenever(userRepository.findById("c1")).thenReturn(Optional.of(customer))
        whenever(appointmentRepository.countByCustomerId("c1")).thenReturn(5)
        
        val result = service.getCustomerDetail("c1")
        
        assertEquals("John Doe", result.fullName)
        assertEquals(5, result.appointmentCount)
        assertEquals("id_documents/doc.jpg", result.idDocumentPath)
    }

    @Test
    fun `should throw exception when customer not found`() {
        whenever(userRepository.findById("invalid")).thenReturn(Optional.empty())
        
        assertThrows<IllegalArgumentException> {
            service.getCustomerDetail("invalid")
        }
    }

    @Test
    fun `should throw exception when user is not a customer`() {
        val admin = User(id = "a1", username = "admin", password = "pass", role = Role.ADMIN,
            fullName = "Admin", email = "admin@test.com")
        
        whenever(userRepository.findById("a1")).thenReturn(Optional.of(admin))
        
        assertThrows<IllegalArgumentException> {
            service.getCustomerDetail("a1")
        }
    }
}
```

**Technical details:**
- Add `findByRole(role: Role, pageable: Pageable): Page<User>` to UserRepository
- Add `findCustomersWithAppointmentsAtBranch(branchId: String, pageable: Pageable): Page<User>` to UserRepository
- Add `countByCustomerId(customerId: String): Int` to AppointmentRepository
- ID document download only for ADMIN role
- Audit log entry created when ID document is accessed

---

### Phase 3: Soft-Delete Cleanup Configuration (Story 18)
**Files**: 
- `src/main/kotlin/com/flowcare/backend/config/controller/SystemConfigController.kt`
- `src/main/kotlin/com/flowcare/backend/config/service/SystemConfigService.kt`
- `src/main/kotlin/com/flowcare/backend/config/dto/RetentionConfigResponse.kt`
- `src/main/kotlin/com/flowcare/backend/config/dto/UpdateRetentionRequest.kt`
- `src/test/kotlin/com/flowcare/backend/config/service/SystemConfigServiceTest.kt`

Admin can view and update the soft-delete retention period configuration.

**Key code changes:**
```kotlin
// config/dto/RetentionConfigResponse.kt
package com.flowcare.backend.config.dto

data class RetentionConfigResponse(
    val retentionPeriodDays: Int,
    val description: String
)

data class UpdateRetentionRequest(
    val retentionPeriodDays: Int
)

// config/service/SystemConfigService.kt
package com.flowcare.backend.config.service

import com.flowcare.backend.config.dto.RetentionConfigResponse
import com.flowcare.backend.config.dto.UpdateRetentionRequest
import com.flowcare.backend.config.model.SystemConfig
import com.flowcare.backend.config.repository.SystemConfigRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class SystemConfigService(
    private val configRepository: SystemConfigRepository
) {
    companion object {
        const val RETENTION_PERIOD_KEY = "RETENTION_PERIOD_DAYS"
        const val DEFAULT_RETENTION_DAYS = 30
    }
    
    fun getRetentionPeriod(): RetentionConfigResponse {
        val config = configRepository.findById(RETENTION_PERIOD_KEY)
            .orElse(createDefaultRetentionConfig())
        
        return RetentionConfigResponse(
            retentionPeriodDays = config.configValue.toInt(),
            description = config.description ?: "Soft-delete retention period"
        )
    }
    
    fun updateRetentionPeriod(request: UpdateRetentionRequest): RetentionConfigResponse {
        require(request.retentionPeriodDays > 0) { 
            "Retention period must be positive" 
        }
        
        val config = configRepository.findById(RETENTION_PERIOD_KEY)
            .orElse(createDefaultRetentionConfig())
        
        val updated = config.copy(
            configValue = request.retentionPeriodDays.toString(),
            updatedAt = LocalDateTime.now()
        )
        
        configRepository.save(updated)
        
        return RetentionConfigResponse(
            retentionPeriodDays = updated.configValue.toInt(),
            description = updated.description ?: "Soft-delete retention period"
        )
    }
    
    private fun createDefaultRetentionConfig(): SystemConfig {
        val config = SystemConfig(
            configKey = RETENTION_PERIOD_KEY,
            configValue = DEFAULT_RETENTION_DAYS.toString(),
            description = "Number of days to retain soft-deleted slots before hard deletion"
        )
        return configRepository.save(config)
    }
}

// config/controller/SystemConfigController.kt
package com.flowcare.backend.config.controller

import com.flowcare.backend.config.dto.RetentionConfigResponse
import com.flowcare.backend.config.dto.UpdateRetentionRequest
import com.flowcare.backend.config.service.SystemConfigService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/config")
class SystemConfigController(
    private val configService: SystemConfigService
) {
    
    @GetMapping("/retention-period")
    @PreAuthorize("hasRole('ADMIN')")
    fun getRetentionPeriod(): RetentionConfigResponse {
        return configService.getRetentionPeriod()
    }
    
    @PutMapping("/retention-period")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateRetentionPeriod(
        @RequestBody request: UpdateRetentionRequest
    ): RetentionConfigResponse {
        return configService.updateRetentionPeriod(request)
    }
}
```

**Test cases:**
```kotlin
// test/kotlin/com/flowcare/backend/config/service/SystemConfigServiceTest.kt
package com.flowcare.backend.config.service

import com.flowcare.backend.config.dto.UpdateRetentionRequest
import com.flowcare.backend.config.model.SystemConfig
import com.flowcare.backend.config.repository.SystemConfigRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*
import kotlin.test.assertEquals

class SystemConfigServiceTest {

    private val configRepository: SystemConfigRepository = mock()
    private val service = SystemConfigService(configRepository)

    @Test
    fun `should return default retention period when not configured`() {
        whenever(configRepository.findById("RETENTION_PERIOD_DAYS"))
            .thenReturn(Optional.empty())
        whenever(configRepository.save(any())).thenAnswer { it.arguments[0] }
        
        val result = service.getRetentionPeriod()
        
        assertEquals(30, result.retentionPeriodDays)
        verify(configRepository).save(any())
    }

    @Test
    fun `should return configured retention period`() {
        val config = SystemConfig(
            configKey = "RETENTION_PERIOD_DAYS",
            configValue = "45",
            description = "Test retention"
        )
        whenever(configRepository.findById("RETENTION_PERIOD_DAYS"))
            .thenReturn(Optional.of(config))
        
        val result = service.getRetentionPeriod()
        
        assertEquals(45, result.retentionPeriodDays)
    }

    @Test
    fun `should update retention period`() {
        val existing = SystemConfig(
            configKey = "RETENTION_PERIOD_DAYS",
            configValue = "30",
            description = "Old value"
        )
        whenever(configRepository.findById("RETENTION_PERIOD_DAYS"))
            .thenReturn(Optional.of(existing))
        whenever(configRepository.save(any())).thenAnswer { it.arguments[0] }
        
        val request = UpdateRetentionRequest(retentionPeriodDays = 60)
        val result = service.updateRetentionPeriod(request)
        
        assertEquals(60, result.retentionPeriodDays)
        verify(configRepository).save(argThat { configValue == "60" })
    }

    @Test
    fun `should reject negative retention period`() {
        val request = UpdateRetentionRequest(retentionPeriodDays = -5)
        
        assertThrows<IllegalArgumentException> {
            service.updateRetentionPeriod(request)
        }
    }

    @Test
    fun `should reject zero retention period`() {
        val request = UpdateRetentionRequest(retentionPeriodDays = 0)
        
        assertThrows<IllegalArgumentException> {
            service.updateRetentionPeriod(request)
        }
    }
}
```

**Technical details:**
- Default retention period: 30 days
- Configuration stored in system_config table
- Only ADMIN role can view/update retention period
- Validation ensures positive retention period values

---

### Phase 4: Soft-Delete Cleanup Execution (Story 18)
**Files**: 
- `src/main/kotlin/com/flowcare/backend/slot/service/SlotCleanupService.kt`
- `src/main/kotlin/com/flowcare/backend/slot/controller/SlotCleanupController.kt`
- `src/main/kotlin/com/flowcare/backend/slot/dto/CleanupSummaryResponse.kt`
- `src/test/kotlin/com/flowcare/backend/slot/service/SlotCleanupServiceTest.kt`

Admin executes cleanup operation to hard-delete soft-deleted slots that exceed retention period.

**Key code changes:**
```kotlin
// slot/dto/CleanupSummaryResponse.kt
package com.flowcare.backend.slot.dto

data class CleanupSummaryResponse(
    val slotsDeleted: Int,
    val retentionPeriodDays: Int,
    val cutoffDate: String
)

// slot/service/SlotCleanupService.kt
package com.flowcare.backend.slot.service

import com.flowcare.backend.appointment.repository.AppointmentRepository
import com.flowcare.backend.audit.service.AuditLogService
import com.flowcare.backend.config.service.SystemConfigService
import com.flowcare.backend.slot.dto.CleanupSummaryResponse
import com.flowcare.backend.slot.repository.SlotRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
class SlotCleanupService(
    private val slotRepository: SlotRepository,
    private val appointmentRepository: AppointmentRepository,
    private val configService: SystemConfigService,
    private val auditLogService: AuditLogService
) {
    private val log = LoggerFactory.getLogger(SlotCleanupService::class.java)
    
    @Transactional
    fun executeCleanup(actorId: String, actorRole: String): CleanupSummaryResponse {
        val retentionConfig = configService.getRetentionPeriod()
        val cutoffDate = OffsetDateTime.now().minusDays(retentionConfig.retentionPeriodDays.toLong())
        
        log.info("Starting cleanup for slots deleted before: {}", cutoffDate)
        
        val slotsToDelete = slotRepository.findSoftDeletedBeforeDate(cutoffDate)
        
        // Filter out slots that have appointments
        val eligibleSlots = slotsToDelete.filter { slot ->
            val hasAppointments = appointmentRepository.existsBySlotId(slot.id)
            if (hasAppointments) {
                log.warn("Skipping slot {} - has associated appointments", slot.id)
            }
            !hasAppointments
        }
        
        eligibleSlots.forEach { slot ->
            // Audit log before deletion
            auditLogService.log(
                actorId = actorId,
                actorRole = actorRole,
                actionType = "SLOT_HARD_DELETED",
                entityType = "Slot",
                entityId = slot.id,
                metadata = mapOf(
                    "branchId" to slot.branchId,
                    "serviceTypeId" to slot.serviceTypeId,
                    "deletedAt" to slot.deletedAt.toString(),
                    "retentionPeriodDays" to retentionConfig.retentionPeriodDays,
                    "hardDeletedAt" to OffsetDateTime.now().toString()
                )
            )
            
            slotRepository.delete(slot)
            log.info("Hard deleted slot: {}", slot.id)
        }
        
        log.info("Cleanup completed. Deleted {} slots", eligibleSlots.size)
        
        return CleanupSummaryResponse(
            slotsDeleted = eligibleSlots.size,
            retentionPeriodDays = retentionConfig.retentionPeriodDays,
            cutoffDate = cutoffDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        )
    }
}

// slot/controller/SlotCleanupController.kt
package com.flowcare.backend.slot.controller

import com.flowcare.backend.auth.service.BranchAccessService
import com.flowcare.backend.slot.dto.CleanupSummaryResponse
import com.flowcare.backend.slot.service.SlotCleanupService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/slots")
class SlotCleanupController(
    private val cleanupService: SlotCleanupService,
    private val branchAccessService: BranchAccessService
) {
    
    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    fun executeCleanup(
        @AuthenticationPrincipal userDetails: UserDetails
    ): CleanupSummaryResponse {
        val user = branchAccessService.getCurrentUser(userDetails.username)
        return cleanupService.executeCleanup(user.id, user.role.name)
    }
}
```

**Test cases:**
```kotlin
// test/kotlin/com/flowcare/backend/slot/service/SlotCleanupServiceTest.kt
package com.flowcare.backend.slot.service

import com.flowcare.backend.appointment.repository.AppointmentRepository
import com.flowcare.backend.audit.service.AuditLogService
import com.flowcare.backend.config.dto.RetentionConfigResponse
import com.flowcare.backend.config.service.SystemConfigService
import com.flowcare.backend.slot.model.Slot
import com.flowcare.backend.slot.repository.SlotRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.OffsetDateTime
import kotlin.test.assertEquals

class SlotCleanupServiceTest {

    private val slotRepository: SlotRepository = mock()
    private val appointmentRepository: AppointmentRepository = mock()
    private val configService: SystemConfigService = mock()
    private val auditLogService: AuditLogService = mock()
    private val service = SlotCleanupService(slotRepository, appointmentRepository, configService, auditLogService)

    @Test
    fun `should hard delete eligible soft-deleted slots`() {
        val retentionConfig = RetentionConfigResponse(30, "Test")
        whenever(configService.getRetentionPeriod()).thenReturn(retentionConfig)
        
        val oldSlot = Slot(
            id = "slot1",
            branchId = "b1",
            serviceTypeId = "s1",
            startAt = OffsetDateTime.now(),
            endAt = OffsetDateTime.now().plusHours(1),
            deletedAt = OffsetDateTime.now().minusDays(35)
        )
        
        whenever(slotRepository.findSoftDeletedBeforeDate(any())).thenReturn(listOf(oldSlot))
        whenever(appointmentRepository.existsBySlotId("slot1")).thenReturn(false)
        
        val result = service.executeCleanup("admin1", "ADMIN")
        
        assertEquals(1, result.slotsDeleted)
        assertEquals(30, result.retentionPeriodDays)
        verify(slotRepository).delete(oldSlot)
        verify(auditLogService).log(
            actorId = "admin1",
            actorRole = "ADMIN",
            actionType = "SLOT_HARD_DELETED",
            entityType = "Slot",
            entityId = "slot1",
            metadata = any()
        )
    }

    @Test
    fun `should skip slots with appointments`() {
        val retentionConfig = RetentionConfigResponse(30, "Test")
        whenever(configService.getRetentionPeriod()).thenReturn(retentionConfig)
        
        val slotWithAppointment = Slot(
            id = "slot2",
            branchId = "b1",
            serviceTypeId = "s1",
            startAt = OffsetDateTime.now(),
            endAt = OffsetDateTime.now().plusHours(1),
            deletedAt = OffsetDateTime.now().minusDays(35)
        )
        
        whenever(slotRepository.findSoftDeletedBeforeDate(any())).thenReturn(listOf(slotWithAppointment))
        whenever(appointmentRepository.existsBySlotId("slot2")).thenReturn(true)
        
        val result = service.executeCleanup("admin1", "ADMIN")
        
        assertEquals(0, result.slotsDeleted)
        verify(slotRepository, never()).delete(any())
    }

    @Test
    fun `should not delete slots within retention period`() {
        val retentionConfig = RetentionConfigResponse(30, "Test")
        whenever(configService.getRetentionPeriod()).thenReturn(retentionConfig)
        
        whenever(slotRepository.findSoftDeletedBeforeDate(any())).thenReturn(emptyList())
        
        val result = service.executeCleanup("admin1", "ADMIN")
        
        assertEquals(0, result.slotsDeleted)
        verify(slotRepository, never()).delete(any())
    }

    @Test
    fun `should be idempotent when run multiple times`() {
        val retentionConfig = RetentionConfigResponse(30, "Test")
        whenever(configService.getRetentionPeriod()).thenReturn(retentionConfig)
        whenever(slotRepository.findSoftDeletedBeforeDate(any())).thenReturn(emptyList())
        
        service.executeCleanup("admin1", "ADMIN")
        service.executeCleanup("admin1", "ADMIN")
        
        verify(slotRepository, times(2)).findSoftDeletedBeforeDate(any())
        verify(slotRepository, never()).delete(any())
    }
}
```

**Technical details:**
- Add `findSoftDeletedBeforeDate(cutoffDate: OffsetDateTime): List<Slot>` to SlotRepository
- Add `existsBySlotId(slotId: String): Boolean` to AppointmentRepository
- Skip slots that have associated appointments (preserve appointment history)
- Audit log created for each hard-deleted slot
- Transaction ensures atomicity of cleanup operation

---

### Phase 5: Audit Log Viewing and Filtering (Story 19)
**Files**: 
- `src/main/kotlin/com/flowcare/backend/audit/controller/AuditLogController.kt`
- `src/main/kotlin/com/flowcare/backend/audit/service/AuditLogQueryService.kt`
- `src/main/kotlin/com/flowcare/backend/audit/dto/AuditLogResponse.kt`
- `src/test/kotlin/com/flowcare/backend/audit/service/AuditLogQueryServiceTest.kt`

Admin views all audit logs, Branch Manager views logs for their branch, with date range filtering and pagination.

**Key code changes:**
```kotlin
// audit/dto/AuditLogResponse.kt
package com.flowcare.backend.audit.dto

import java.time.OffsetDateTime

data class AuditLogResponse(
    val id: String,
    val actorId: String,
    val actorRole: String,
    val actionType: String,
    val entityType: String,
    val entityId: String,
    val timestamp: OffsetDateTime,
    val metadata: Map<String, Any>?
)

// audit/service/AuditLogQueryService.kt
package com.flowcare.backend.audit.service

import com.flowcare.backend.audit.dto.AuditLogResponse
import com.flowcare.backend.audit.repository.AuditLogRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class AuditLogQueryService(
    private val auditLogRepository: AuditLogRepository
) {
    
    fun listAllLogs(
        startDate: LocalDate?,
        endDate: LocalDate?,
        pageable: Pageable
    ): Page<AuditLogResponse> {
        val logs = if (startDate != null && endDate != null) {
            val start = startDate.atStartOfDay().atOffset(OffsetDateTime.now().offset)
            val end = endDate.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().offset)
            auditLogRepository.findByTimestampBetween(start, end, pageable)
        } else {
            auditLogRepository.findAll(pageable)
        }
        
        return logs.map { log ->
            AuditLogResponse(
                id = log.id,
                actorId = log.actorId,
                actorRole = log.actorRole,
                actionType = log.actionType,
                entityType = log.entityType,
                entityId = log.entityId,
                timestamp = log.timestamp,
                metadata = log.metadata
            )
        }
    }
    
    fun listLogsByBranch(
        branchId: String,
        startDate: LocalDate?,
        endDate: LocalDate?,
        pageable: Pageable
    ): Page<AuditLogResponse> {
        val logs = if (startDate != null && endDate != null) {
            val start = startDate.atStartOfDay().atOffset(OffsetDateTime.now().offset)
            val end = endDate.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().offset)
            auditLogRepository.findByBranchIdAndTimestampBetween(branchId, start, end, pageable)
        } else {
            auditLogRepository.findByBranchId(branchId, pageable)
        }
        
        return logs.map { log ->
            AuditLogResponse(
                id = log.id,
                actorId = log.actorId,
                actorRole = log.actorRole,
                actionType = log.actionType,
                entityType = log.entityType,
                entityId = log.entityId,
                timestamp = log.timestamp,
                metadata = log.metadata
            )
        }
    }
}

// audit/controller/AuditLogController.kt
package com.flowcare.backend.audit.controller

import com.flowcare.backend.audit.dto.AuditLogResponse
import com.flowcare.backend.audit.service.AuditLogQueryService
import com.flowcare.backend.auth.service.BranchAccessService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/audit-logs")
class AuditLogController(
    private val auditLogQueryService: AuditLogQueryService,
    private val branchAccessService: BranchAccessService
) {
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun listAuditLogs(
        @AuthenticationPrincipal userDetails: UserDetails,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        pageable: Pageable
    ): Page<AuditLogResponse> {
        val user = branchAccessService.getCurrentUser(userDetails.username)
        
        return if (branchAccessService.isAdmin(user)) {
            auditLogQueryService.listAllLogs(startDate, endDate, pageable)
        } else {
            val branchId = branchAccessService.getUserBranchId(user)
            auditLogQueryService.listLogsByBranch(branchId, startDate, endDate, pageable)
        }
    }
}
```

**Test cases:**
```kotlin
// test/kotlin/com/flowcare/backend/audit/service/AuditLogQueryServiceTest.kt
package com.flowcare.backend.audit.service

import com.flowcare.backend.audit.model.AuditLog
import com.flowcare.backend.audit.repository.AuditLogRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.assertEquals

class AuditLogQueryServiceTest {

    private val auditLogRepository: AuditLogRepository = mock()
    private val service = AuditLogQueryService(auditLogRepository)

    @Test
    fun `should list all logs without date filter`() {
        val pageable = PageRequest.of(0, 10)
        val logs = listOf(
            AuditLog(
                id = "log1",
                actorId = "user1",
                actorRole = "ADMIN",
                actionType = "APPOINTMENT_CREATED",
                entityType = "Appointment",
                entityId = "appt1",
                timestamp = OffsetDateTime.now()
            )
        )
        whenever(auditLogRepository.findAll(pageable)).thenReturn(PageImpl(logs))
        
        val result = service.listAllLogs(null, null, pageable)
        
        assertEquals(1, result.content.size)
        assertEquals("APPOINTMENT_CREATED", result.content[0].actionType)
    }

    @Test
    fun `should list logs with date range filter`() {
        val pageable = PageRequest.of(0, 10)
        val startDate = LocalDate.of(2026, 3, 1)
        val endDate = LocalDate.of(2026, 3, 31)
        
        val logs = listOf(
            AuditLog(
                id = "log1",
                actorId = "user1",
                actorRole = "ADMIN",
                actionType = "SLOT_CREATED",
                entityType = "Slot",
                entityId = "slot1",
                timestamp = OffsetDateTime.now()
            )
        )
        
        whenever(auditLogRepository.findByTimestampBetween(any(), any(), eq(pageable)))
            .thenReturn(PageImpl(logs))
        
        val result = service.listAllLogs(startDate, endDate, pageable)
        
        assertEquals(1, result.content.size)
        verify(auditLogRepository).findByTimestampBetween(any(), any(), eq(pageable))
    }

    @Test
    fun `should list logs by branch without date filter`() {
        val pageable = PageRequest.of(0, 10)
        val logs = listOf(
            AuditLog(
                id = "log1",
                actorId = "mgr1",
                actorRole = "BRANCH_MANAGER",
                actionType = "SLOT_UPDATED",
                entityType = "Slot",
                entityId = "slot1",
                timestamp = OffsetDateTime.now(),
                metadata = mapOf("branchId" to "b1")
            )
        )
        
        whenever(auditLogRepository.findByBranchId("b1", pageable))
            .thenReturn(PageImpl(logs))
        
        val result = service.listLogsByBranch("b1", null, null, pageable)
        
        assertEquals(1, result.content.size)
        verify(auditLogRepository).findByBranchId("b1", pageable)
    }

    @Test
    fun `should list logs by branch with date range`() {
        val pageable = PageRequest.of(0, 10)
        val startDate = LocalDate.of(2026, 3, 1)
        val endDate = LocalDate.of(2026, 3, 31)
        
        whenever(auditLogRepository.findByBranchIdAndTimestampBetween(eq("b1"), any(), any(), eq(pageable)))
            .thenReturn(PageImpl(emptyList()))
        
        val result = service.listLogsByBranch("b1", startDate, endDate, pageable)
        
        assertEquals(0, result.content.size)
        verify(auditLogRepository).findByBranchIdAndTimestampBetween(eq("b1"), any(), any(), eq(pageable))
    }
}
```

**Technical details:**
- Add `findByTimestampBetween(start: OffsetDateTime, end: OffsetDateTime, pageable: Pageable): Page<AuditLog>` to AuditLogRepository
- Add `findByBranchId(branchId: String, pageable: Pageable): Page<AuditLog>` to AuditLogRepository (queries metadata JSONB field)
- Add `findByBranchIdAndTimestampBetween(branchId: String, start: OffsetDateTime, end: OffsetDateTime, pageable: Pageable): Page<AuditLog>` to AuditLogRepository
- Date range is optional, defaults to all logs
- Logs sorted by timestamp descending (most recent first)

---

### Phase 6: Audit Log CSV Export (Story 19)
**Files**: 
- `src/main/kotlin/com/flowcare/backend/audit/service/AuditLogExportService.kt`
- `src/main/kotlin/com/flowcare/backend/audit/controller/AuditLogExportController.kt`
- `src/test/kotlin/com/flowcare/backend/audit/service/AuditLogExportServiceTest.kt`

Admin exports audit logs as CSV file with date range filtering.

**Key code changes:**
```kotlin
// audit/service/AuditLogExportService.kt
package com.flowcare.backend.audit.service

import com.flowcare.backend.audit.repository.AuditLogRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
class AuditLogExportService(
    private val auditLogRepository: AuditLogRepository,
    private val objectMapper: ObjectMapper
) {
    
    fun exportToCsv(startDate: LocalDate?, endDate: LocalDate?): ByteArray {
        val logs = if (startDate != null && endDate != null) {
            val start = startDate.atStartOfDay().atOffset(OffsetDateTime.now().offset)
            val end = endDate.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().offset)
            auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end)
        } else {
            auditLogRepository.findAllByOrderByTimestampDesc()
        }
        
        val outputStream = ByteArrayOutputStream()
        val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
        
        // Write CSV header
        writer.write("ID,Actor ID,Actor Role,Action Type,Entity Type,Entity ID,Timestamp,Metadata\n")
        
        // Write data rows
        logs.forEach { log ->
            val metadataJson = if (log.metadata != null) {
                objectMapper.writeValueAsString(log.metadata).replace("\"", "\"\"")
            } else {
                ""
            }
            
            val row = listOf(
                log.id,
                log.actorId,
                log.actorRole,
                log.actionType,
                log.entityType,
                log.entityId,
                log.timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "\"$metadataJson\""
            ).joinToString(",")
            
            writer.write("$row\n")
        }
        
        writer.flush()
        return outputStream.toByteArray()
    }
}

// audit/controller/AuditLogExportController.kt
package com.flowcare.backend.audit.controller

import com.flowcare.backend.audit.service.AuditLogExportService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/admin/audit-logs")
class AuditLogExportController(
    private val exportService: AuditLogExportService
) {
    
    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    fun exportAuditLogs(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?
    ): ResponseEntity<ByteArray> {
        val csvData = exportService.exportToCsv(startDate, endDate)
        
        val filename = if (startDate != null && endDate != null) {
            "audit_logs_${startDate.format(DateTimeFormatter.ISO_DATE)}_to_${endDate.format(DateTimeFormatter.ISO_DATE)}.csv"
        } else {
            "audit_logs_all.csv"
        }
        
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .body(csvData)
    }
}
```

**Test cases:**
```kotlin
// test/kotlin/com/flowcare/backend/audit/service/AuditLogExportServiceTest.kt
package com.flowcare.backend.audit.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.flowcare.backend.audit.model.AuditLog
import com.flowcare.backend.audit.repository.AuditLogRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.assertTrue

class AuditLogExportServiceTest {

    private val auditLogRepository: AuditLogRepository = mock()
    private val objectMapper = ObjectMapper()
    private val service = AuditLogExportService(auditLogRepository, objectMapper)

    @Test
    fun `should export all logs to CSV`() {
        val logs = listOf(
            AuditLog(
                id = "log1",
                actorId = "user1",
                actorRole = "ADMIN",
                actionType = "APPOINTMENT_CREATED",
                entityType = "Appointment",
                entityId = "appt1",
                timestamp = OffsetDateTime.now(),
                metadata = mapOf("branchId" to "b1", "customerId" to "c1")
            )
        )
        
        whenever(auditLogRepository.findAllByOrderByTimestampDesc()).thenReturn(logs)
        
        val csvData = service.exportToCsv(null, null)
        val csvString = String(csvData, Charsets.UTF_8)
        
        assertTrue(csvString.contains("ID,Actor ID,Actor Role,Action Type"))
        assertTrue(csvString.contains("log1,user1,ADMIN,APPOINTMENT_CREATED"))
        assertTrue(csvString.contains("branchId"))
    }

    @Test
    fun `should export logs with date range to CSV`() {
        val startDate = LocalDate.of(2026, 3, 1)
        val endDate = LocalDate.of(2026, 3, 31)
        
        val logs = listOf(
            AuditLog(
                id = "log2",
                actorId = "mgr1",
                actorRole = "BRANCH_MANAGER",
                actionType = "SLOT_CREATED",
                entityType = "Slot",
                entityId = "slot1",
                timestamp = OffsetDateTime.now(),
                metadata = null
            )
        )
        
        whenever(auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any()))
            .thenReturn(logs)
        
        val csvData = service.exportToCsv(startDate, endDate)
        val csvString = String(csvData, Charsets.UTF_8)
        
        assertTrue(csvString.contains("log2,mgr1,BRANCH_MANAGER,SLOT_CREATED"))
        verify(auditLogRepository).findByTimestampBetweenOrderByTimestampDesc(any(), any())
    }

    @Test
    fun `should handle empty audit logs`() {
        whenever(auditLogRepository.findAllByOrderByTimestampDesc()).thenReturn(emptyList())
        
        val csvData = service.exportToCsv(null, null)
        val csvString = String(csvData, Charsets.UTF_8)
        
        assertTrue(csvString.contains("ID,Actor ID,Actor Role"))
        assertTrue(csvString.lines().size == 2) // Header + empty line
    }

    @Test
    fun `should escape quotes in metadata JSON`() {
        val logs = listOf(
            AuditLog(
                id = "log3",
                actorId = "user1",
                actorRole = "ADMIN",
                actionType = "TEST",
                entityType = "Test",
                entityId = "t1",
                timestamp = OffsetDateTime.now(),
                metadata = mapOf("note" to "Customer said \"urgent\"")
            )
        )
        
        whenever(auditLogRepository.findAllByOrderByTimestampDesc()).thenReturn(logs)
        
        val csvData = service.exportToCsv(null, null)
        val csvString = String(csvData, Charsets.UTF_8)
        
        assertTrue(csvString.contains("\"\"urgent\"\""))
    }
}
```

**Technical details:**
- Add `findAllByOrderByTimestampDesc(): List<AuditLog>` to AuditLogRepository
- Add `findByTimestampBetweenOrderByTimestampDesc(start: OffsetDateTime, end: OffsetDateTime): List<AuditLog>` to AuditLogRepository
- Metadata serialized as JSON string in CSV
- CSV escapes quotes in metadata
- Only ADMIN role can export
- Filename includes date range if provided

---

### Phase 7: Staff Listing (Story 20)
**Files**: 
- `src/main/kotlin/com/flowcare/backend/staff/controller/StaffController.kt`
- `src/main/kotlin/com/flowcare/backend/staff/service/StaffService.kt`
- `src/main/kotlin/com/flowcare/backend/staff/dto/StaffListResponse.kt`
- `src/main/kotlin/com/flowcare/backend/staff/dto/StaffDetailResponse.kt`
- `src/test/kotlin/com/flowcare/backend/staff/service/StaffServiceTest.kt`

Admin views all staff, Branch Manager views staff in their branch, with pagination and service assignments in detail view.

**Key code changes:**
```kotlin
// staff/dto/StaffListResponse.kt
package com.flowcare.backend.staff.dto

data class StaffListResponse(
    val id: String,
    val fullName: String,
    val email: String,
    val branchId: String,
    val branchName: String,
    val isActive: Boolean
)

data class StaffDetailResponse(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val branchId: String,
    val branchName: String,
    val isActive: Boolean,
    val serviceAssignments: List<ServiceAssignmentDto>
)

data class ServiceAssignmentDto(
    val serviceTypeId: String,
    val serviceTypeName: String
)

// staff/service/StaffService.kt
package com.flowcare.backend.staff.service

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.branch.repository.BranchRepository
import com.flowcare.backend.service.repository.ServiceTypeRepository
import com.flowcare.backend.staff.dto.ServiceAssignmentDto
import com.flowcare.backend.staff.dto.StaffDetailResponse
import com.flowcare.backend.staff.dto.StaffListResponse
import com.flowcare.backend.staff.repository.StaffServiceTypeRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class StaffService(
    private val userRepository: UserRepository,
    private val branchRepository: BranchRepository,
    private val staffServiceTypeRepository: StaffServiceTypeRepository,
    private val serviceTypeRepository: ServiceTypeRepository
) {
    
    fun listAllStaff(pageable: Pageable): Page<StaffListResponse> {
        return userRepository.findByRole(Role.STAFF, pageable)
            .map { user ->
                val branch = branchRepository.findById(user.branchId!!)
                    .orElseThrow { IllegalStateException("Branch not found for staff") }
                
                StaffListResponse(
                    id = user.id,
                    fullName = user.fullName,
                    email = user.email,
                    branchId = user.branchId!!,
                    branchName = branch.name,
                    isActive = user.isActive
                )
            }
    }
    
    fun listStaffByBranch(branchId: String, pageable: Pageable): Page<StaffListResponse> {
        return userRepository.findByRoleAndBranchId(Role.STAFF, branchId, pageable)
            .map { user ->
                val branch = branchRepository.findById(user.branchId!!)
                    .orElseThrow { IllegalStateException("Branch not found") }
                
                StaffListResponse(
                    id = user.id,
                    fullName = user.fullName,
                    email = user.email,
                    branchId = user.branchId!!,
                    branchName = branch.name,
                    isActive = user.isActive
                )
            }
    }
    
    fun getStaffDetail(staffId: String): StaffDetailResponse {
        val user = userRepository.findById(staffId)
            .orElseThrow { IllegalArgumentException("Staff not found") }
        
        if (user.role != Role.STAFF) {
            throw IllegalArgumentException("User is not a staff member")
        }
        
        val branch = branchRepository.findById(user.branchId!!)
            .orElseThrow { IllegalStateException("Branch not found") }
        
        val assignments = staffServiceTypeRepository.findByStaffId(staffId)
        val serviceAssignments = assignments.map { assignment ->
            val serviceType = serviceTypeRepository.findById(assignment.serviceTypeId)
                .orElseThrow { IllegalStateException("Service type not found") }
            
            ServiceAssignmentDto(
                serviceTypeId = serviceType.id,
                serviceTypeName = serviceType.name
            )
        }
        
        return StaffDetailResponse(
            id = user.id,
            fullName = user.fullName,
            email = user.email,
            phone = user.phone,
            branchId = user.branchId!!,
            branchName = branch.name,
            isActive = user.isActive,
            serviceAssignments = serviceAssignments
        )
    }
}

// staff/controller/StaffController.kt
package com.flowcare.backend.staff.controller

import com.flowcare.backend.auth.service.BranchAccessService
import com.flowcare.backend.staff.dto.StaffDetailResponse
import com.flowcare.backend.staff.dto.StaffListResponse
import com.flowcare.backend.staff.service.StaffService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/staff")
class StaffController(
    private val staffService: StaffService,
    private val branchAccessService: BranchAccessService
) {
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun listStaff(
        @AuthenticationPrincipal userDetails: UserDetails,
        pageable: Pageable
    ): Page<StaffListResponse> {
        val user = branchAccessService.getCurrentUser(userDetails.username)
        
        return if (branchAccessService.isAdmin(user)) {
            staffService.listAllStaff(pageable)
        } else {
            val branchId = branchAccessService.getUserBranchId(user)
            staffService.listStaffByBranch(branchId, pageable)
        }
    }
    
    @GetMapping("/{staffId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun getStaffDetail(
        @PathVariable staffId: String,
        @AuthenticationPrincipal userDetails: UserDetails
    ): StaffDetailResponse {
        val user = branchAccessService.getCurrentUser(userDetails.username)
        val staffDetail = staffService.getStaffDetail(staffId)
        
        // Branch Manager can only view staff in their branch
        if (!branchAccessService.isAdmin(user)) {
            val branchId = branchAccessService.getUserBranchId(user)
            if (staffDetail.branchId != branchId) {
                throw IllegalAccessException("Cannot access staff from another branch")
            }
        }
        
        return staffDetail
    }
}
```

**Test cases:**
```kotlin
// test/kotlin/com/flowcare/backend/staff/service/StaffServiceTest.kt
package com.flowcare.backend.staff.service

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.branch.model.Branch
import com.flowcare.backend.branch.repository.BranchRepository
import com.flowcare.backend.service.model.ServiceType
import com.flowcare.backend.service.repository.ServiceTypeRepository
import com.flowcare.backend.staff.model.StaffServiceType
import com.flowcare.backend.staff.repository.StaffServiceTypeRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.*
import kotlin.test.assertEquals

class StaffServiceTest {

    private val userRepository: UserRepository = mock()
    private val branchRepository: BranchRepository = mock()
    private val staffServiceTypeRepository: StaffServiceTypeRepository = mock()
    private val serviceTypeRepository: ServiceTypeRepository = mock()
    private val service = StaffService(userRepository, branchRepository, staffServiceTypeRepository, serviceTypeRepository)

    @Test
    fun `should list all staff with pagination`() {
        val pageable = PageRequest.of(0, 10)
        val branch = Branch(id = "b1", name = "Main Branch", location = "Muscat", contactNumber = "123")
        val staff = listOf(
            User(id = "s1", username = "staff1", password = "pass", role = Role.STAFF,
                fullName = "Staff One", email = "staff1@test.com", branchId = "b1")
        )
        
        whenever(userRepository.findByRole(Role.STAFF, pageable)).thenReturn(PageImpl(staff))
        whenever(branchRepository.findById("b1")).thenReturn(Optional.of(branch))
        
        val result = service.listAllStaff(pageable)
        
        assertEquals(1, result.content.size)
        assertEquals("Staff One", result.content[0].fullName)
        assertEquals("Main Branch", result.content[0].branchName)
    }

    @Test
    fun `should list staff by branch`() {
        val pageable = PageRequest.of(0, 10)
        val branch = Branch(id = "b1", name = "Main Branch", location = "Muscat", contactNumber = "123")
        val staff = listOf(
            User(id = "s1", username = "staff1", password = "pass", role = Role.STAFF,
                fullName = "Staff One", email = "staff1@test.com", branchId = "b1")
        )
        
        whenever(userRepository.findByRoleAndBranchId(Role.STAFF, "b1", pageable))
            .thenReturn(PageImpl(staff))
        whenever(branchRepository.findById("b1")).thenReturn(Optional.of(branch))
        
        val result = service.listStaffByBranch("b1", pageable)
        
        assertEquals(1, result.content.size)
        assertEquals("b1", result.content[0].branchId)
    }

    @Test
    fun `should get staff detail with service assignments`() {
        val branch = Branch(id = "b1", name = "Main Branch", location = "Muscat", contactNumber = "123")
        val staff = User(id = "s1", username = "staff1", password = "pass", role = Role.STAFF,
            fullName = "Staff One", email = "staff1@test.com", branchId = "b1", phone = "99999999")
        val serviceType = ServiceType(id = "st1", name = "Consultation", description = "Medical consultation",
            durationMinutes = 30, branchId = "b1")
        val assignment = StaffServiceType(id = "a1", staffId = "s1", serviceTypeId = "st1")
        
        whenever(userRepository.findById("s1")).thenReturn(Optional.of(staff))
        whenever(branchRepository.findById("b1")).thenReturn(Optional.of(branch))
        whenever(staffServiceTypeRepository.findByStaffId("s1")).thenReturn(listOf(assignment))
        whenever(serviceTypeRepository.findById("st1")).thenReturn(Optional.of(serviceType))
        
        val result = service.getStaffDetail("s1")
        
        assertEquals("Staff One", result.fullName)
        assertEquals("Main Branch", result.branchName)
        assertEquals(1, result.serviceAssignments.size)
        assertEquals("Consultation", result.serviceAssignments[0].serviceTypeName)
    }

    @Test
    fun `should throw exception when staff not found`() {
        whenever(userRepository.findById("invalid")).thenReturn(Optional.empty())
        
        assertThrows<IllegalArgumentException> {
            service.getStaffDetail("invalid")
        }
    }

    @Test
    fun `should throw exception when user is not staff`() {
        val customer = User(id = "c1", username = "customer", password = "pass", role = Role.CUSTOMER,
            fullName = "Customer", email = "customer@test.com")
        
        whenever(userRepository.findById("c1")).thenReturn(Optional.of(customer))
        
        assertThrows<IllegalArgumentException> {
            service.getStaffDetail("c1")
        }
    }
}
```

**Technical details:**
- Add `findByRoleAndBranchId(role: Role, branchId: String, pageable: Pageable): Page<User>` to UserRepository
- Staff list shows basic info (name, email, branch, active status)
- Staff detail includes service type assignments
- Branch Managers can only view staff in their branch
- Staff sorted by branch then name

---

## Technical Considerations

**Dependencies:**
- No new external packages required
- Uses existing Spring Data JPA, Jackson for JSON/CSV

**Edge Cases:**
- Customer with no appointments still appears in list for Branch Manager if they have any appointment at that branch
- Soft-deleted slots with appointments are skipped during cleanup
- Empty audit logs export still generates valid CSV with headers
- Staff without service assignments show empty list in detail view
- Date range filters are inclusive of start date, exclusive of end date + 1

**Testing Strategy:**
- Unit tests for each service class with mocked repositories
- Controller tests verify role-based access control
- Repository tests verify custom queries (especially JSONB queries for audit logs)
- Integration tests for CSV export format validation
- Test pagination edge cases (empty results, single page, multiple pages)

**Performance:**
- Pagination prevents memory issues with large datasets
- Audit log queries indexed on timestamp and metadata->branchId
- CSV export loads all matching records into memory (acceptable for compliance exports)
- Cleanup operation uses transaction to ensure atomicity

**Security:**
- ID document access restricted to ADMIN only
- ID document access is audit logged
- Branch Managers cannot access other branches' data
- Only ADMIN can export audit logs
- Only ADMIN can configure retention period and execute cleanup

## Success Criteria

- [ ] Admin can view all customers with pagination
- [ ] Branch Manager can view customers with appointments at their branch
- [ ] Admin can download customer ID documents
- [ ] ID document access is logged in audit log
- [ ] Admin can view and update retention period configuration
- [ ] Admin can execute cleanup to hard-delete expired soft-deleted slots
- [ ] Cleanup skips slots with appointments
- [ ] Cleanup creates audit log entries for each hard-deleted slot
- [ ] Admin can view all audit logs with date filtering
- [ ] Branch Manager can view audit logs for their branch
- [ ] Admin can export audit logs as CSV with date filtering
- [ ] CSV includes all audit log fields with metadata as JSON string
- [ ] Admin can view all staff with pagination
- [ ] Branch Manager can view staff in their branch
- [ ] Staff detail view includes service type assignments
- [ ] All endpoints enforce role-based access control
- [ ] All operations have appropriate test coverage
