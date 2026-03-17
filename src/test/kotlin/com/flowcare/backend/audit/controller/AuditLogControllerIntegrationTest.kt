package com.flowcare.backend.audit.controller

import com.flowcare.backend.appointment.repository.AppointmentRepository
import com.flowcare.backend.audit.model.AuditLog
import com.flowcare.backend.audit.repository.AuditLogRepository
import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.branch.model.Branch
import com.flowcare.backend.branch.repository.BranchRepository
import com.flowcare.backend.service.repository.ServiceTypeRepository
import com.flowcare.backend.slot.repository.SlotRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.OffsetDateTime

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var branchRepository: BranchRepository
    @Autowired private lateinit var auditLogRepository: AuditLogRepository
    @Autowired private lateinit var appointmentRepository: AppointmentRepository
    @Autowired private lateinit var slotRepository: SlotRepository
    @Autowired private lateinit var serviceTypeRepository: ServiceTypeRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun setup() {
        appointmentRepository.deleteAll()
        auditLogRepository.deleteAll()
        slotRepository.deleteAll()
        serviceTypeRepository.deleteAll()
        branchRepository.deleteAll()
        userRepository.deleteAll()

        branchRepository.save(Branch(id = "br_test", name = "Test Branch", city = "Muscat", address = "Test", timezone = "Asia/Muscat"))
        userRepository.save(User(id = "adm_1", username = "adm", password = passwordEncoder.encode("pass"), role = Role.ADMIN, fullName = "Admin", email = "adm@test.com"))
        userRepository.save(User(id = "cust_1", username = "cust", password = passwordEncoder.encode("pass"), role = Role.CUSTOMER, fullName = "Customer", email = "cust@test.com"))

        auditLogRepository.save(AuditLog(id = "audit_1", actorId = "adm_1", actorRole = "ADMIN", actionType = "TEST_ACTION", entityType = "Slot", entityId = "slot_1", metadata = mapOf("branchId" to "br_test"), timestamp = OffsetDateTime.now()))
    }

    @Test
    fun `should list audit logs as admin`() {
        mockMvc.perform(get("/api/audit-logs").with(httpBasic("adm", "pass")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].actionType").value("TEST_ACTION"))
    }

    @Test
    fun `should reject customer access to audit logs`() {
        mockMvc.perform(get("/api/audit-logs").with(httpBasic("cust", "pass")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should export audit logs as CSV`() {
        mockMvc.perform(get("/api/admin/audit-logs/export").with(httpBasic("adm", "pass")))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "text/csv"))
    }

    @Test
    fun `should reject customer access to export`() {
        mockMvc.perform(get("/api/admin/audit-logs/export").with(httpBasic("cust", "pass")))
            .andExpect(status().isForbidden)
    }
}
