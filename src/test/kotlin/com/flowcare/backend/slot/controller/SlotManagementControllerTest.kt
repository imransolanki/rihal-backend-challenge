package com.flowcare.backend.slot.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.flowcare.backend.appointment.repository.AppointmentRepository
import com.flowcare.backend.audit.repository.AuditLogRepository
import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.branch.model.Branch
import com.flowcare.backend.branch.repository.BranchRepository
import com.flowcare.backend.service.model.ServiceType
import com.flowcare.backend.service.repository.ServiceTypeRepository
import com.flowcare.backend.slot.dto.BulkCreateSlotsRequest
import com.flowcare.backend.slot.dto.CreateSlotRequest
import com.flowcare.backend.slot.dto.UpdateSlotRequest
import com.flowcare.backend.slot.model.Slot
import com.flowcare.backend.slot.repository.SlotRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.OffsetDateTime

@SpringBootTest
@AutoConfigureMockMvc
class SlotManagementControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var slotRepository: SlotRepository
    @Autowired private lateinit var branchRepository: BranchRepository
    @Autowired private lateinit var serviceTypeRepository: ServiceTypeRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var auditLogRepository: AuditLogRepository
    @Autowired private lateinit var appointmentRepository: AppointmentRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var branch: Branch
    private lateinit var branch2: Branch
    private lateinit var serviceType: ServiceType
    private lateinit var admin: User
    private lateinit var manager: User
    private lateinit var staff: User
    private lateinit var customer: User

    @BeforeEach
    fun setup() {
        appointmentRepository.deleteAll()
        auditLogRepository.deleteAll()
        slotRepository.deleteAll()
        serviceTypeRepository.deleteAll()
        branchRepository.deleteAll()
        userRepository.deleteAll()

        branch = branchRepository.save(Branch(
            id = "branch_1", name = "Branch One", city = "Muscat", address = "123 St"
        ))
        branch2 = branchRepository.save(Branch(
            id = "branch_2", name = "Branch Two", city = "Salalah", address = "456 St"
        ))
        serviceType = serviceTypeRepository.save(ServiceType(
            id = "svc_1", branchId = branch.id, name = "Consultation", durationMinutes = 30
        ))

        val encoded = passwordEncoder.encode("password")
        admin = userRepository.save(User(
            id = "admin_1", username = "admin", password = encoded,
            role = Role.ADMIN, fullName = "Admin", email = "admin@test.com"
        ))
        manager = userRepository.save(User(
            id = "mgr_1", username = "manager", password = encoded,
            role = Role.BRANCH_MANAGER, fullName = "Manager", email = "mgr@test.com",
            branchId = branch.id
        ))
        staff = userRepository.save(User(
            id = "staff_1", username = "staff", password = encoded,
            role = Role.STAFF, fullName = "Staff", email = "staff@test.com",
            branchId = branch.id
        ))
        customer = userRepository.save(User(
            id = "cust_1", username = "customer", password = encoded,
            role = Role.CUSTOMER, fullName = "Customer", email = "cust@test.com"
        ))
    }

    private fun createSlotRequest(branchId: String = branch.id) = CreateSlotRequest(
        branchId = branchId, serviceTypeId = serviceType.id,
        startAt = OffsetDateTime.now().plusDays(1),
        endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
    )

    private fun saveTestSlot(
        bookedCount: Int = 0,
        deletedAt: OffsetDateTime? = null,
        staffId: String? = null
    ): Slot = slotRepository.save(Slot(
        id = "slot_test_${System.nanoTime()}",
        branchId = branch.id, serviceTypeId = serviceType.id,
        staffId = staffId,
        startAt = OffsetDateTime.now().plusDays(1),
        endAt = OffsetDateTime.now().plusDays(1).plusHours(1),
        capacity = 5, bookedCount = bookedCount, deletedAt = deletedAt
    ))

    // --- CREATE ---

    @Test
    fun `admin should create slot successfully`() {
        mockMvc.perform(post("/api/slots")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createSlotRequest())))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.branchId").value(branch.id))
            .andExpect(jsonPath("$.bookedCount").value(0))

        val auditLogs = auditLogRepository.findAll()
        assert(auditLogs.any { it.actionType == "SLOT_CREATED" })
    }

    @Test
    fun `branch manager should create slot for own branch`() {
        mockMvc.perform(post("/api/slots")
            .with(httpBasic("manager", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createSlotRequest())))
            .andExpect(status().isCreated)
    }

    @Test
    fun `branch manager should not create slot for other branch`() {
        mockMvc.perform(post("/api/slots")
            .with(httpBasic("manager", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createSlotRequest(branch2.id))))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `staff should not create slots`() {
        mockMvc.perform(post("/api/slots")
            .with(httpBasic("staff", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createSlotRequest())))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `customer should not create slots`() {
        mockMvc.perform(post("/api/slots")
            .with(httpBasic("customer", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createSlotRequest())))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should reject slot with end time before start time`() {
        val request = CreateSlotRequest(
            branchId = branch.id, serviceTypeId = serviceType.id,
            startAt = OffsetDateTime.now().plusDays(1).plusHours(2),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        mockMvc.perform(post("/api/slots")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should reject slot in the past`() {
        val request = CreateSlotRequest(
            branchId = branch.id, serviceTypeId = serviceType.id,
            startAt = OffsetDateTime.now().minusDays(1),
            endAt = OffsetDateTime.now().minusDays(1).plusHours(1)
        )
        mockMvc.perform(post("/api/slots")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should reject slot with non-existent branch`() {
        val request = createSlotRequest("nonexistent_branch")
        mockMvc.perform(post("/api/slots")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest)
    }

    // --- BULK CREATE ---

    @Test
    fun `should create multiple slots in bulk`() {
        val request = BulkCreateSlotsRequest(listOf(
            CreateSlotRequest(branch.id, serviceType.id, null,
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1)),
            CreateSlotRequest(branch.id, serviceType.id, null,
                OffsetDateTime.now().plusDays(2), OffsetDateTime.now().plusDays(2).plusHours(1))
        ))
        mockMvc.perform(post("/api/slots/bulk")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.successCount").value(2))
            .andExpect(jsonPath("$.failureCount").value(0))
    }

    @Test
    fun `should handle partial failures in bulk creation`() {
        val request = BulkCreateSlotsRequest(listOf(
            CreateSlotRequest(branch.id, serviceType.id, null,
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1)),
            CreateSlotRequest("invalid_branch", serviceType.id, null,
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1))
        ))
        // Admin has access to all branches, so branch access check passes, but validation catches invalid branch
        mockMvc.perform(post("/api/slots/bulk")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.successCount").value(1))
            .andExpect(jsonPath("$.failureCount").value(1))
            .andExpect(jsonPath("$.failures[0].reason").value("Branch not found: invalid_branch"))
    }

    // --- UPDATE ---

    @Test
    fun `should update slot staff assignment`() {
        val slot = saveTestSlot()
        val request = UpdateSlotRequest(staffId = staff.id)
        mockMvc.perform(patch("/api/slots/${slot.id}")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.staffId").value(staff.id))

        val auditLogs = auditLogRepository.findAll()
        assert(auditLogs.any { it.actionType == "SLOT_UPDATED" && it.entityId == slot.id })
    }

    @Test
    fun `should remove staff assignment from slot`() {
        val slot = saveTestSlot(staffId = staff.id)
        val request = UpdateSlotRequest(removeStaffAssignment = true)
        mockMvc.perform(patch("/api/slots/${slot.id}")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.staffId").isEmpty)
    }

    @Test
    fun `should reject time update for booked slot`() {
        val slot = saveTestSlot(bookedCount = 1)
        val request = UpdateSlotRequest(startAt = OffsetDateTime.now().plusDays(3))
        mockMvc.perform(patch("/api/slots/${slot.id}")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should allow staff update for booked slot`() {
        val slot = saveTestSlot(bookedCount = 1)
        val request = UpdateSlotRequest(staffId = staff.id)
        mockMvc.perform(patch("/api/slots/${slot.id}")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.staffId").value(staff.id))
    }

    @Test
    fun `should reject update of deleted slot`() {
        val slot = saveTestSlot(deletedAt = OffsetDateTime.now())
        val request = UpdateSlotRequest(staffId = staff.id)
        mockMvc.perform(patch("/api/slots/${slot.id}")
            .with(httpBasic("admin", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict)
    }

    // --- SOFT DELETE ---

    @Test
    fun `should soft delete unbooked slot`() {
        val slot = saveTestSlot()
        mockMvc.perform(delete("/api/slots/${slot.id}")
            .with(httpBasic("admin", "password")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deletedAt").exists())

        val auditLogs = auditLogRepository.findAll()
        assert(auditLogs.any { it.actionType == "SLOT_SOFT_DELETED" && it.entityId == slot.id })
    }

    @Test
    fun `should reject soft delete of booked slot`() {
        val slot = saveTestSlot(bookedCount = 1)
        mockMvc.perform(delete("/api/slots/${slot.id}")
            .with(httpBasic("admin", "password")))
            .andExpect(status().isConflict)
    }

    @Test
    fun `should reject soft delete of already deleted slot`() {
        val slot = saveTestSlot(deletedAt = OffsetDateTime.now())
        mockMvc.perform(delete("/api/slots/${slot.id}")
            .with(httpBasic("admin", "password")))
            .andExpect(status().isConflict)
    }

    // --- LIST & GET ---

    @Test
    fun `should list slots excluding deleted by default`() {
        saveTestSlot()
        saveTestSlot(deletedAt = OffsetDateTime.now())

        mockMvc.perform(get("/api/slots")
            .with(httpBasic("admin", "password"))
            .param("branchId", branch.id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `admin should see deleted slots when requested`() {
        saveTestSlot()
        saveTestSlot(deletedAt = OffsetDateTime.now())

        mockMvc.perform(get("/api/slots")
            .with(httpBasic("admin", "password"))
            .param("branchId", branch.id)
            .param("includeDeleted", "true"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `branch manager should not see deleted slots even when requested`() {
        saveTestSlot()
        saveTestSlot(deletedAt = OffsetDateTime.now())

        mockMvc.perform(get("/api/slots")
            .with(httpBasic("manager", "password"))
            .param("includeDeleted", "true"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `branch manager list is scoped to own branch`() {
        saveTestSlot() // branch_1
        // Save a slot in branch_2
        val svc2 = serviceTypeRepository.save(ServiceType(
            id = "svc_2", branchId = branch2.id, name = "Other", durationMinutes = 30
        ))
        slotRepository.save(Slot(
            id = "slot_other_branch", branchId = branch2.id, serviceTypeId = svc2.id,
            startAt = OffsetDateTime.now().plusDays(1), endAt = OffsetDateTime.now().plusDays(1).plusHours(1),
            capacity = 1, bookedCount = 0
        ))

        mockMvc.perform(get("/api/slots")
            .with(httpBasic("manager", "password")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].branchId").value(branch.id))
    }

    @Test
    fun `should get single slot by id`() {
        val slot = saveTestSlot()
        mockMvc.perform(get("/api/slots/${slot.id}")
            .with(httpBasic("admin", "password")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(slot.id))
    }

    @Test
    fun `should return 400 for non-existent slot`() {
        mockMvc.perform(get("/api/slots/nonexistent")
            .with(httpBasic("admin", "password")))
            .andExpect(status().isBadRequest)
    }
}
