# Story 5 & 6: Public Discovery - Browse Branches, Services & Available Slots - Implementation Plan

## Overview
Implement public API endpoints that allow unauthenticated users to browse FlowCare branches, view services offered at each branch, and see available appointment slots. These endpoints include response caching for performance and filter out past/unavailable slots automatically.

## Architecture
- GET /api/public/branches → List all active branches
- GET /api/public/branches/{branchId}/services → Get branch details + services
- GET /api/public/slots?branchId=X&serviceTypeId=Y&date=YYYY-MM-DD → List available slots
- Public endpoints (no authentication required)
- Response caching with 5-minute TTL using Spring Cache
- Slots filtered: future only, not soft-deleted, capacity > booked_count
- Staff details (ID + name) included when slot is assigned

## Implementation Phases

### Phase 1: Database Schema - Add booked_count to Slots
**Files**: 
- `src/main/resources/db/migration/V5__add_booked_count_to_slots.sql`
- `src/main/kotlin/com/flowcare/backend/slot/model/Slot.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/slot/model/SlotBookedCountTest.kt`

Add booked_count column to track how many appointments are booked against each slot's capacity.

**Key code changes:**

```sql
-- src/main/resources/db/migration/V5__add_booked_count_to_slots.sql
ALTER TABLE slots 
ADD COLUMN booked_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_slots_availability ON slots(branch_id, service_type_id, start_at, booked_count, deleted_at);

COMMENT ON COLUMN slots.booked_count IS 'Number of appointments booked for this slot';
```

```kotlin
// Update src/main/kotlin/com/flowcare/backend/slot/model/Slot.kt
package com.flowcare.backend.slot.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Entity
@Table(name = "slots")
data class Slot(
    @Id
    val id: String,

    @Column(name = "branch_id", nullable = false)
    val branchId: String,

    @Column(name = "service_type_id", nullable = false)
    val serviceTypeId: String,

    @Column(name = "staff_id")
    val staffId: String? = null,

    @Column(name = "start_at", nullable = false)
    val startAt: OffsetDateTime,

    @Column(name = "end_at", nullable = false)
    val endAt: OffsetDateTime,

    @Column(nullable = false)
    val capacity: Int = 1,

    @Column(name = "booked_count", nullable = false)
    var bookedCount: Int = 0,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "deleted_at")
    var deletedAt: OffsetDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun isAvailable(): Boolean = bookedCount < capacity && deletedAt == null && isActive
}
```

**Test cases for this phase:**

```kotlin
// src/test/kotlin/com/flowcare/backend/slot/model/SlotBookedCountTest.kt
package com.flowcare.backend.slot.model

import com.flowcare.backend.slot.repository.SlotRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.assertj.core.api.Assertions.assertThat
import java.time.OffsetDateTime

@DataJpaTest
class SlotBookedCountTest {

    @Autowired
    private lateinit var slotRepository: SlotRepository

    @Test
    fun `slot with booked_count less than capacity is available`() {
        val slot = Slot(
            id = "slot_test_001",
            branchId = "branch_001",
            serviceTypeId = "service_001",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1),
            capacity = 3,
            bookedCount = 2
        )
        slotRepository.save(slot)
        
        assertThat(slot.isAvailable()).isTrue()
    }

    @Test
    fun `slot with booked_count equal to capacity is not available`() {
        val slot = Slot(
            id = "slot_test_002",
            branchId = "branch_001",
            serviceTypeId = "service_001",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1),
            capacity = 1,
            bookedCount = 1
        )
        
        assertThat(slot.isAvailable()).isFalse()
    }

    @Test
    fun `soft-deleted slot is not available`() {
        val slot = Slot(
            id = "slot_test_003",
            branchId = "branch_001",
            serviceTypeId = "service_001",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1),
            capacity = 1,
            bookedCount = 0,
            deletedAt = OffsetDateTime.now()
        )
        
        assertThat(slot.isAvailable()).isFalse()
    }
}
```

**Technical details:**
- Migration adds booked_count with default 0 for existing slots
- Index on (branch_id, service_type_id, start_at, booked_count, deleted_at) optimizes availability queries
- isAvailable() helper method encapsulates availability logic

---

### Phase 2: Branch DTOs and Controller
**Files**: 
- `src/main/kotlin/com/flowcare/backend/branch/dto/BranchResponse.kt`
- `src/main/kotlin/com/flowcare/backend/branch/controller/BranchController.kt`
- `src/main/kotlin/com/flowcare/backend/branch/service/BranchService.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/branch/controller/BranchControllerTest.kt`
- `src/test/kotlin/com/flowcare/backend/branch/service/BranchServiceTest.kt`

Create public endpoint to list all active branches with caching.

**Key code changes:**

```kotlin
// src/main/kotlin/com/flowcare/backend/branch/dto/BranchResponse.kt
package com.flowcare.backend.branch.dto

data class BranchResponse(
    val id: String,
    val name: String,
    val city: String,
    val address: String,
    val timezone: String
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/branch/service/BranchService.kt
package com.flowcare.backend.branch.service

import com.flowcare.backend.branch.dto.BranchResponse
import com.flowcare.backend.branch.repository.BranchRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class BranchService(
    private val branchRepository: BranchRepository
) {
    
    @Cacheable(value = ["branches"], key = "'all'")
    fun getAllActiveBranches(): List<BranchResponse> {
        return branchRepository.findAll()
            .filter { it.isActive }
            .map { branch ->
                BranchResponse(
                    id = branch.id,
                    name = branch.name,
                    city = branch.city,
                    address = branch.address,
                    timezone = branch.timezone
                )
            }
    }
}
```

```kotlin
// src/main/kotlin/com/flowcare/backend/branch/controller/BranchController.kt
package com.flowcare.backend.branch.controller

import com.flowcare.backend.branch.dto.BranchResponse
import com.flowcare.backend.branch.service.BranchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/public/branches")
class BranchController(
    private val branchService: BranchService
) {

    @GetMapping
    fun getAllBranches(): ResponseEntity<List<BranchResponse>> {
        val branches = branchService.getAllActiveBranches()
        return ResponseEntity.ok(branches)
    }
}
```

**Test cases for this phase:**

```kotlin
// src/test/kotlin/com/flowcare/backend/branch/controller/BranchControllerTest.kt
package com.flowcare.backend.branch.controller

import com.flowcare.backend.branch.model.Branch
import com.flowcare.backend.branch.repository.BranchRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class BranchControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @BeforeEach
    fun setup() {
        branchRepository.deleteAll()
    }

    @Test
    fun `GET all branches returns active branches only`() {
        branchRepository.save(Branch(
            id = "branch_001",
            name = "Muscat Branch",
            city = "Muscat",
            address = "123 Main St",
            isActive = true
        ))
        branchRepository.save(Branch(
            id = "branch_002",
            name = "Salalah Branch",
            city = "Salalah",
            address = "456 South St",
            isActive = false
        ))

        mockMvc.perform(get("/api/public/branches"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("branch_001"))
            .andExpect(jsonPath("$[0].name").value("Muscat Branch"))
    }

    @Test
    fun `GET all branches returns empty list when no active branches`() {
        mockMvc.perform(get("/api/public/branches"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
```

**Technical details:**
- @Cacheable with 5-minute TTL (configured in Phase 5)
- Filter only active branches
- Public endpoint, no authentication required

---
### Phase 3: Service DTOs and Branch Services Endpoint
**Files**: 
- `src/main/kotlin/com/flowcare/backend/service/dto/ServiceTypeResponse.kt`
- `src/main/kotlin/com/flowcare/backend/service/dto/BranchWithServicesResponse.kt`
- `src/main/kotlin/com/flowcare/backend/service/service/ServiceTypeService.kt`
- `src/main/kotlin/com/flowcare/backend/branch/controller/BranchController.kt` (update)
- `src/main/kotlin/com/flowcare/backend/branch/service/BranchService.kt` (update)

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/service/service/ServiceTypeServiceTest.kt`
- `src/test/kotlin/com/flowcare/backend/branch/controller/BranchServicesControllerTest.kt`

Create endpoint to get branch details with all services offered at that branch.

**Key code changes:**

```kotlin
// src/main/kotlin/com/flowcare/backend/service/dto/ServiceTypeResponse.kt
package com.flowcare.backend.service.dto

data class ServiceTypeResponse(
    val id: String,
    val name: String,
    val description: String?,
    val durationMinutes: Int
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/service/dto/BranchWithServicesResponse.kt
package com.flowcare.backend.service.dto

import com.flowcare.backend.branch.dto.BranchResponse

data class BranchWithServicesResponse(
    val branch: BranchResponse,
    val services: List<ServiceTypeResponse>
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/service/service/ServiceTypeService.kt
package com.flowcare.backend.service.service

import com.flowcare.backend.service.dto.ServiceTypeResponse
import com.flowcare.backend.service.repository.ServiceTypeRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class ServiceTypeService(
    private val serviceTypeRepository: ServiceTypeRepository
) {
    
    @Cacheable(value = ["services"], key = "#branchId")
    fun getServicesByBranch(branchId: String): List<ServiceTypeResponse> {
        return serviceTypeRepository.findAll()
            .filter { it.branchId == branchId && it.isActive }
            .map { service ->
                ServiceTypeResponse(
                    id = service.id,
                    name = service.name,
                    description = service.description,
                    durationMinutes = service.durationMinutes
                )
            }
    }
}
```

```kotlin
// Update src/main/kotlin/com/flowcare/backend/branch/service/BranchService.kt
package com.flowcare.backend.branch.service

import com.flowcare.backend.branch.dto.BranchResponse
import com.flowcare.backend.branch.repository.BranchRepository
import com.flowcare.backend.service.dto.BranchWithServicesResponse
import com.flowcare.backend.service.service.ServiceTypeService
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class BranchService(
    private val branchRepository: BranchRepository,
    private val serviceTypeService: ServiceTypeService
) {
    
    @Cacheable(value = ["branches"], key = "'all'")
    fun getAllActiveBranches(): List<BranchResponse> {
        return branchRepository.findAll()
            .filter { it.isActive }
            .map { branch ->
                BranchResponse(
                    id = branch.id,
                    name = branch.name,
                    city = branch.city,
                    address = branch.address,
                    timezone = branch.timezone
                )
            }
    }
    
    @Cacheable(value = ["branchWithServices"], key = "#branchId")
    fun getBranchWithServices(branchId: String): BranchWithServicesResponse? {
        val branch = branchRepository.findById(branchId).orElse(null) ?: return null
        
        if (!branch.isActive) return null
        
        val branchResponse = BranchResponse(
            id = branch.id,
            name = branch.name,
            city = branch.city,
            address = branch.address,
            timezone = branch.timezone
        )
        
        val services = serviceTypeService.getServicesByBranch(branchId)
        
        return BranchWithServicesResponse(
            branch = branchResponse,
            services = services
        )
    }
}
```

```kotlin
// Update src/main/kotlin/com/flowcare/backend/branch/controller/BranchController.kt
package com.flowcare.backend.branch.controller

import com.flowcare.backend.branch.dto.BranchResponse
import com.flowcare.backend.branch.service.BranchService
import com.flowcare.backend.service.dto.BranchWithServicesResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/public/branches")
class BranchController(
    private val branchService: BranchService
) {

    @GetMapping
    fun getAllBranches(): ResponseEntity<List<BranchResponse>> {
        val branches = branchService.getAllActiveBranches()
        return ResponseEntity.ok(branches)
    }
    
    @GetMapping("/{branchId}/services")
    fun getBranchServices(@PathVariable branchId: String): ResponseEntity<BranchWithServicesResponse> {
        val result = branchService.getBranchWithServices(branchId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }
}
```

**Test cases for this phase:**

```kotlin
// src/test/kotlin/com/flowcare/backend/branch/controller/BranchServicesControllerTest.kt
package com.flowcare.backend.branch.controller

import com.flowcare.backend.branch.model.Branch
import com.flowcare.backend.branch.repository.BranchRepository
import com.flowcare.backend.service.model.ServiceType
import com.flowcare.backend.service.repository.ServiceTypeRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class BranchServicesControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var serviceTypeRepository: ServiceTypeRepository

    @BeforeEach
    fun setup() {
        serviceTypeRepository.deleteAll()
        branchRepository.deleteAll()
    }

    @Test
    fun `GET branch services returns branch with services`() {
        val branch = branchRepository.save(Branch(
            id = "branch_001",
            name = "Muscat Branch",
            city = "Muscat",
            address = "123 Main St",
            isActive = true
        ))
        
        serviceTypeRepository.save(ServiceType(
            id = "service_001",
            branchId = branch.id,
            name = "General Consultation",
            description = "Basic health checkup",
            durationMinutes = 30,
            isActive = true
        ))
        
        serviceTypeRepository.save(ServiceType(
            id = "service_002",
            branchId = branch.id,
            name = "Lab Test",
            description = "Blood work",
            durationMinutes = 15,
            isActive = true
        ))

        mockMvc.perform(get("/api/public/branches/branch_001/services"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.branch.id").value("branch_001"))
            .andExpect(jsonPath("$.branch.name").value("Muscat Branch"))
            .andExpect(jsonPath("$.services.length()").value(2))
            .andExpect(jsonPath("$.services[0].name").value("General Consultation"))
    }

    @Test
    fun `GET branch services returns 404 for non-existent branch`() {
        mockMvc.perform(get("/api/public/branches/invalid_branch/services"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET branch services returns empty services list when no services configured`() {
        branchRepository.save(Branch(
            id = "branch_002",
            name = "Empty Branch",
            city = "Salalah",
            address = "456 South St",
            isActive = true
        ))

        mockMvc.perform(get("/api/public/branches/branch_002/services"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.services.length()").value(0))
    }
}
```

**Technical details:**
- Cached response for branch + services combination
- Returns 404 if branch doesn't exist or is inactive
- Empty services list if no services configured for branch

---

### Phase 4: Slot DTOs and Available Slots Endpoint
**Files**: 
- `src/main/kotlin/com/flowcare/backend/slot/dto/AvailableSlotResponse.kt`
- `src/main/kotlin/com/flowcare/backend/slot/dto/StaffBasicInfo.kt`
- `src/main/kotlin/com/flowcare/backend/slot/service/SlotService.kt`
- `src/main/kotlin/com/flowcare/backend/slot/controller/SlotController.kt`
- `src/main/kotlin/com/flowcare/backend/slot/repository/SlotRepository.kt` (update)

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/slot/service/SlotServiceTest.kt`
- `src/main/kotlin/com/flowcare/backend/slot/controller/SlotControllerTest.kt`

Create endpoint to list available slots with filtering by branch, service, and optional date.

**Key code changes:**

```kotlin
// src/main/kotlin/com/flowcare/backend/slot/dto/StaffBasicInfo.kt
package com.flowcare.backend.slot.dto

data class StaffBasicInfo(
    val id: String,
    val fullName: String
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/slot/dto/AvailableSlotResponse.kt
package com.flowcare.backend.slot.dto

import java.time.OffsetDateTime

data class AvailableSlotResponse(
    val id: String,
    val branchId: String,
    val serviceTypeId: String,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val capacity: Int,
    val availableCapacity: Int,
    val staff: StaffBasicInfo?
)
```

```kotlin
// Update src/main/kotlin/com/flowcare/backend/slot/repository/SlotRepository.kt
package com.flowcare.backend.slot.repository

import com.flowcare.backend.slot.model.Slot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface SlotRepository : JpaRepository<Slot, String> {
    
    @Query("""
        SELECT s FROM Slot s 
        WHERE s.branchId = :branchId 
        AND s.serviceTypeId = :serviceTypeId 
        AND s.startAt > :now 
        AND s.deletedAt IS NULL 
        AND s.isActive = true 
        AND s.bookedCount < s.capacity
        ORDER BY s.startAt ASC
    """)
    fun findAvailableSlots(
        branchId: String,
        serviceTypeId: String,
        now: OffsetDateTime
    ): List<Slot>
    
    @Query("""
        SELECT s FROM Slot s 
        WHERE s.branchId = :branchId 
        AND s.serviceTypeId = :serviceTypeId 
        AND DATE(s.startAt) = DATE(:date)
        AND s.startAt > :now 
        AND s.deletedAt IS NULL 
        AND s.isActive = true 
        AND s.bookedCount < s.capacity
        ORDER BY s.startAt ASC
    """)
    fun findAvailableSlotsByDate(
        branchId: String,
        serviceTypeId: String,
        date: OffsetDateTime,
        now: OffsetDateTime
    ): List<Slot>
}
```

```kotlin
// src/main/kotlin/com/flowcare/backend/slot/service/SlotService.kt
package com.flowcare.backend.slot.service

import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.slot.dto.AvailableSlotResponse
import com.flowcare.backend.slot.dto.StaffBasicInfo
import com.flowcare.backend.slot.repository.SlotRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
class SlotService(
    private val slotRepository: SlotRepository,
    private val userRepository: UserRepository
) {
    
    @Cacheable(value = ["availableSlots"], key = "#branchId + '_' + #serviceTypeId + '_' + (#date?.toString() ?: 'all')")
    fun getAvailableSlots(
        branchId: String,
        serviceTypeId: String,
        date: LocalDate?
    ): List<AvailableSlotResponse> {
        val now = OffsetDateTime.now()
        
        val slots = if (date != null) {
            val dateTime = date.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime()
            slotRepository.findAvailableSlotsByDate(branchId, serviceTypeId, dateTime, now)
        } else {
            slotRepository.findAvailableSlots(branchId, serviceTypeId, now)
        }
        
        val staffIds = slots.mapNotNull { it.staffId }.distinct()
        val staffMap = if (staffIds.isNotEmpty()) {
            userRepository.findAllById(staffIds).associateBy { it.id }
        } else {
            emptyMap()
        }
        
        return slots.map { slot ->
            AvailableSlotResponse(
                id = slot.id,
                branchId = slot.branchId,
                serviceTypeId = slot.serviceTypeId,
                startAt = slot.startAt,
                endAt = slot.endAt,
                capacity = slot.capacity,
                availableCapacity = slot.capacity - slot.bookedCount,
                staff = slot.staffId?.let { staffId ->
                    staffMap[staffId]?.let { user ->
                        StaffBasicInfo(id = user.id, fullName = user.fullName)
                    }
                }
            )
        }
    }
}
```

```kotlin
// src/main/kotlin/com/flowcare/backend/slot/controller/SlotController.kt
package com.flowcare.backend.slot.controller

import com.flowcare.backend.slot.dto.AvailableSlotResponse
import com.flowcare.backend.slot.service.SlotService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/public/slots")
class SlotController(
    private val slotService: SlotService
) {

    @GetMapping
    fun getAvailableSlots(
        @RequestParam branchId: String,
        @RequestParam serviceTypeId: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?
    ): ResponseEntity<List<AvailableSlotResponse>> {
        val slots = slotService.getAvailableSlots(branchId, serviceTypeId, date)
        return ResponseEntity.ok(slots)
    }
}
```

**Test cases for this phase:**

```kotlin
// src/test/kotlin/com/flowcare/backend/slot/controller/SlotControllerTest.kt
package com.flowcare.backend.slot.controller

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.branch.model.Branch
import com.flowcare.backend.branch.repository.BranchRepository
import com.flowcare.backend.service.model.ServiceType
import com.flowcare.backend.service.repository.ServiceTypeRepository
import com.flowcare.backend.slot.model.Slot
import com.flowcare.backend.slot.repository.SlotRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.OffsetDateTime

@SpringBootTest
@AutoConfigureMockMvc
class SlotControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var slotRepository: SlotRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var serviceTypeRepository: ServiceTypeRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun setup() {
        slotRepository.deleteAll()
        serviceTypeRepository.deleteAll()
        branchRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `GET available slots returns only future unbooked slots`() {
        val branch = branchRepository.save(Branch(
            id = "branch_001",
            name = "Muscat Branch",
            city = "Muscat",
            address = "123 Main St",
            isActive = true
        ))
        
        val service = serviceTypeRepository.save(ServiceType(
            id = "service_001",
            branchId = branch.id,
            name = "Consultation",
            durationMinutes = 30,
            isActive = true
        ))
        
        // Future available slot
        slotRepository.save(Slot(
            id = "slot_001",
            branchId = branch.id,
            serviceTypeId = service.id,
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusMinutes(30),
            capacity = 1,
            bookedCount = 0
        ))
        
        // Past slot (should not appear)
        slotRepository.save(Slot(
            id = "slot_002",
            branchId = branch.id,
            serviceTypeId = service.id,
            startAt = OffsetDateTime.now().minusDays(1),
            endAt = OffsetDateTime.now().minusDays(1).plusMinutes(30),
            capacity = 1,
            bookedCount = 0
        ))
        
        // Fully booked slot (should not appear)
        slotRepository.save(Slot(
            id = "slot_003",
            branchId = branch.id,
            serviceTypeId = service.id,
            startAt = OffsetDateTime.now().plusDays(2),
            endAt = OffsetDateTime.now().plusDays(2).plusMinutes(30),
            capacity = 1,
            bookedCount = 1
        ))

        mockMvc.perform(get("/api/public/slots")
            .param("branchId", branch.id)
            .param("serviceTypeId", service.id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("slot_001"))
    }

    @Test
    fun `GET available slots with date filter returns only slots for that date`() {
        val branch = branchRepository.save(Branch(
            id = "branch_001",
            name = "Muscat Branch",
            city = "Muscat",
            address = "123 Main St",
            isActive = true
        ))
        
        val service = serviceTypeRepository.save(ServiceType(
            id = "service_001",
            branchId = branch.id,
            name = "Consultation",
            durationMinutes = 30,
            isActive = true
        ))
        
        val targetDate = OffsetDateTime.now().plusDays(5)
        
        slotRepository.save(Slot(
            id = "slot_001",
            branchId = branch.id,
            serviceTypeId = service.id,
            startAt = targetDate.withHour(10).withMinute(0),
            endAt = targetDate.withHour(10).withMinute(30),
            capacity = 1,
            bookedCount = 0
        ))
        
        slotRepository.save(Slot(
            id = "slot_002",
            branchId = branch.id,
            serviceTypeId = service.id,
            startAt = targetDate.plusDays(1).withHour(10).withMinute(0),
            endAt = targetDate.plusDays(1).withHour(10).withMinute(30),
            capacity = 1,
            bookedCount = 0
        ))

        mockMvc.perform(get("/api/public/slots")
            .param("branchId", branch.id)
            .param("serviceTypeId", service.id)
            .param("date", targetDate.toLocalDate().toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("slot_001"))
    }

    @Test
    fun `GET available slots includes staff name when slot is assigned`() {
        val branch = branchRepository.save(Branch(
            id = "branch_001",
            name = "Muscat Branch",
            city = "Muscat",
            address = "123 Main St",
            isActive = true
        ))
        
        val service = serviceTypeRepository.save(ServiceType(
            id = "service_001",
            branchId = branch.id,
            name = "Consultation",
            durationMinutes = 30,
            isActive = true
        ))
        
        val staff = userRepository.save(User(
            id = "staff_001",
            username = "dr.smith",
            password = "hashed",
            role = Role.STAFF,
            fullName = "Dr. John Smith",
            email = "dr.smith@flowcare.com",
            branchId = branch.id
        ))
        
        slotRepository.save(Slot(
            id = "slot_001",
            branchId = branch.id,
            serviceTypeId = service.id,
            staffId = staff.id,
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusMinutes(30),
            capacity = 1,
            bookedCount = 0
        ))

        mockMvc.perform(get("/api/public/slots")
            .param("branchId", branch.id)
            .param("serviceTypeId", service.id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].staff.id").value("staff_001"))
            .andExpect(jsonPath("$[0].staff.fullName").value("Dr. John Smith"))
    }

    @Test
    fun `GET available slots returns empty list when no slots available`() {
        mockMvc.perform(get("/api/public/slots")
            .param("branchId", "branch_001")
            .param("serviceTypeId", "service_001"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
```

**Technical details:**
- Query filters: future slots, not deleted, active, has available capacity
- Date parameter is optional (ISO 8601 format: YYYY-MM-DD)
- Staff info fetched in batch to avoid N+1 queries
- Slots sorted by startAt ascending

---

### Phase 5: Cache Configuration
**Files**: 
- `src/main/kotlin/com/flowcare/backend/config/CacheConfig.kt`
- `build.gradle.kts` (update)

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/config/CacheConfigTest.kt`

Configure Spring Cache with 5-minute TTL for public endpoints.

**Key code changes:**

```kotlin
// Update build.gradle.kts - add cache dependency
dependencies {
    // ... existing dependencies ...
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
}
```

```kotlin
// src/main/kotlin/com/flowcare/backend/config/CacheConfig.kt
package com.flowcare.backend.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager(
            "branches",
            "services",
            "branchWithServices",
            "availableSlots"
        )
        cacheManager.setCaffeine(caffeineCacheBuilder())
        return cacheManager
    }

    private fun caffeineCacheBuilder(): Caffeine<Any, Any> {
        return Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1000)
            .recordStats()
    }
}
```

```kotlin
// Update src/main/kotlin/com/flowcare/backend/BackendApplication.kt
package com.flowcare.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@EnableCaching
class BackendApplication

fun main(args: Array<String>) {
    runApplication<BackendApplication>(*args)
}
```

**Test cases for this phase:**

```kotlin
// src/test/kotlin/com/flowcare/backend/config/CacheConfigTest.kt
package com.flowcare.backend.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.assertj.core.api.Assertions.assertThat

@SpringBootTest
class CacheConfigTest {

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Test
    fun `cache manager has required caches configured`() {
        assertThat(cacheManager.getCache("branches")).isNotNull
        assertThat(cacheManager.getCache("services")).isNotNull
        assertThat(cacheManager.getCache("branchWithServices")).isNotNull
        assertThat(cacheManager.getCache("availableSlots")).isNotNull
    }
}
```

**Technical details:**
- Caffeine cache with 5-minute TTL
- Maximum 1000 entries per cache
- Stats recording enabled for monitoring

---

## Technical Considerations

**Dependencies:**
- Spring Boot Starter Cache
- Caffeine cache library (3.1.8)

**Edge Cases:**
- Empty results return empty arrays, not null
- Invalid branch/service IDs return 404 or empty list
- Date filter with past date returns empty list
- Soft-deleted slots never appear in results
- Inactive branches/services excluded from results

**Testing Strategy:**
- Unit tests for service layer logic
- Integration tests for controller endpoints
- Test cache behavior (optional, can verify via logs)
- Test filtering logic (past slots, booked slots, deleted slots)

**Performance:**
- Response caching reduces database load
- Batch fetch staff info to avoid N+1 queries
- Database indexes on availability query fields
- Slots sorted at database level

**Security:**
- Public endpoints, no authentication required
- No sensitive data exposed (passwords, internal notes)
- Staff info limited to ID and name only

## Success Criteria

- [ ] GET /api/public/branches returns all active branches
- [ ] GET /api/public/branches/{id}/services returns branch with services list
- [ ] GET /api/public/slots filters by branch and service (required params)
- [ ] GET /api/public/slots supports optional date filter (YYYY-MM-DD format)
- [ ] Only future slots with available capacity are returned
- [ ] Soft-deleted and inactive slots are excluded
- [ ] Staff name displayed when slot is assigned to staff
- [ ] Empty results return empty arrays with 200 OK
- [ ] Responses are cached for 5 minutes
- [ ] All endpoints work without authentication
- [ ] Database migration adds booked_count column successfully
- [ ] Slot.isAvailable() method correctly checks availability
