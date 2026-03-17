package com.flowcare.backend.staff.controller

import com.flowcare.backend.appointment.repository.AppointmentRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class StaffControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var branchRepository: BranchRepository
    @Autowired private lateinit var appointmentRepository: AppointmentRepository
    @Autowired private lateinit var auditLogRepository: AuditLogRepository
    @Autowired private lateinit var slotRepository: SlotRepository
    @Autowired private lateinit var serviceTypeRepository: ServiceTypeRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var admin: User
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

        val branch = branchRepository.save(Branch(id = "br_test", name = "Test Branch", city = "Muscat", address = "Test", timezone = "Asia/Muscat"))

        admin = userRepository.save(User(id = "adm_1", username = "adm", password = passwordEncoder.encode("pass"), role = Role.ADMIN, fullName = "Admin", email = "adm@test.com"))
        staff = userRepository.save(User(id = "staff_1", username = "staff", password = passwordEncoder.encode("pass"), role = Role.STAFF, fullName = "Test Staff", email = "staff@test.com", branchId = branch.id))
        customer = userRepository.save(User(id = "cust_1", username = "cust", password = passwordEncoder.encode("pass"), role = Role.CUSTOMER, fullName = "Customer", email = "cust@test.com"))
    }

    @Test
    fun `should list staff as admin`() {
        mockMvc.perform(get("/api/staff").with(httpBasic("adm", "pass")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].fullName").value("Test Staff"))
    }

    @Test
    fun `should get staff detail as admin`() {
        mockMvc.perform(get("/api/staff/${staff.id}").with(httpBasic("adm", "pass")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fullName").value("Test Staff"))
    }

    @Test
    fun `should reject customer access to staff list`() {
        mockMvc.perform(get("/api/staff").with(httpBasic("cust", "pass")))
            .andExpect(status().isForbidden)
    }
}
