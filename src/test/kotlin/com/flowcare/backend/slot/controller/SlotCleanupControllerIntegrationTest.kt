package com.flowcare.backend.slot.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.flowcare.backend.appointment.repository.AppointmentRepository
import com.flowcare.backend.audit.repository.AuditLogRepository
import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.branch.model.Branch
import com.flowcare.backend.branch.repository.BranchRepository
import com.flowcare.backend.config.model.SystemConfig
import com.flowcare.backend.config.repository.SystemConfigRepository
import com.flowcare.backend.service.model.ServiceType
import com.flowcare.backend.service.repository.ServiceTypeRepository
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
class SlotCleanupControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var branchRepository: BranchRepository
    @Autowired private lateinit var slotRepository: SlotRepository
    @Autowired private lateinit var serviceTypeRepository: ServiceTypeRepository
    @Autowired private lateinit var appointmentRepository: AppointmentRepository
    @Autowired private lateinit var auditLogRepository: AuditLogRepository
    @Autowired private lateinit var systemConfigRepository: SystemConfigRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var admin: User
    private lateinit var customer: User

    @BeforeEach
    fun setup() {
        appointmentRepository.deleteAll()
        auditLogRepository.deleteAll()
        slotRepository.deleteAll()
        serviceTypeRepository.deleteAll()
        systemConfigRepository.deleteAll()
        branchRepository.deleteAll()
        userRepository.deleteAll()

        val branch = branchRepository.save(Branch(id = "br_test", name = "Test Branch", city = "Muscat", address = "Test", timezone = "Asia/Muscat"))
        val service = serviceTypeRepository.save(ServiceType(id = "svc_test", branchId = branch.id, name = "Test Service", description = "Desc", durationMinutes = 30))

        admin = userRepository.save(User(id = "adm_1", username = "adm", password = passwordEncoder.encode("pass"), role = Role.ADMIN, fullName = "Admin", email = "adm@test.com"))
        customer = userRepository.save(User(id = "cust_1", username = "cust", password = passwordEncoder.encode("pass"), role = Role.CUSTOMER, fullName = "Customer", email = "cust@test.com"))

        systemConfigRepository.save(SystemConfig(key = "slot.retention.days", value = "30", description = "Retention period"))

        // Create a soft-deleted slot older than 30 days
        slotRepository.save(Slot(id = "slot_old", branchId = branch.id, serviceTypeId = service.id, startAt = OffsetDateTime.now().minusDays(60), endAt = OffsetDateTime.now().minusDays(60).plusMinutes(30), isActive = false, deletedAt = OffsetDateTime.now().minusDays(31)))
    }

    @Test
    fun `should get retention period as admin`() {
        mockMvc.perform(get("/api/admin/config/retention-period").with(httpBasic("adm", "pass")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.retentionPeriodDays").value(30))
    }

    @Test
    fun `should update retention period as admin`() {
        mockMvc.perform(
            put("/api/admin/config/retention-period")
                .with(httpBasic("adm", "pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("retentionPeriodDays" to 60)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.retentionPeriodDays").value(60))
    }

    @Test
    fun `should reject customer access to config`() {
        mockMvc.perform(get("/api/admin/config/retention-period").with(httpBasic("cust", "pass")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should execute cleanup as admin`() {
        mockMvc.perform(post("/api/admin/slots/cleanup").with(httpBasic("adm", "pass")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.slotsDeleted").value(1))
    }

    @Test
    fun `should reject customer access to cleanup`() {
        mockMvc.perform(post("/api/admin/slots/cleanup").with(httpBasic("cust", "pass")))
            .andExpect(status().isForbidden)
    }
}
