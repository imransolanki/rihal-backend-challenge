package com.flowcare.backend.common.controller

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.branch.model.Branch
import com.flowcare.backend.branch.repository.BranchRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class TestControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var branchRepository: BranchRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun setup() {
        userRepository.deleteAll()
        branchRepository.deleteAll()
        
        // Create branch that users will reference
        branchRepository.save(Branch(
            id = "br_muscat_001",
            name = "Test Branch",
            city = "Muscat",
            address = "Test Address",
            isActive = true
        ))
        
        userRepository.saveAll(listOf(
            User(id = "t_admin", username = "t_admin", password = passwordEncoder.encode("password"),
                role = Role.ADMIN, fullName = "Admin", email = "t_admin@test.com"),
            User(id = "t_mgr", username = "t_mgr", password = passwordEncoder.encode("password"),
                role = Role.BRANCH_MANAGER, fullName = "Manager", email = "t_mgr@test.com", branchId = "br_muscat_001"),
            User(id = "t_staff", username = "t_staff", password = passwordEncoder.encode("password"),
                role = Role.STAFF, fullName = "Staff", email = "t_staff@test.com", branchId = "br_muscat_001"),
            User(id = "t_cust", username = "t_cust", password = passwordEncoder.encode("password"),
                role = Role.CUSTOMER, fullName = "Customer", email = "t_cust@test.com")
        ))
    }

    @Test fun `protected endpoint requires authentication`() {
        mockMvc.perform(get("/api/test/protected")).andExpect(status().isUnauthorized)
    }

    @Test fun `protected endpoint accessible with valid credentials`() {
        mockMvc.perform(get("/api/test/protected").with(httpBasic("t_admin", "password")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("ADMIN"))
    }

    @Test fun `invalid credentials return 401`() {
        mockMvc.perform(get("/api/test/protected").with(httpBasic("t_admin", "wrong")))
            .andExpect(status().isUnauthorized)
    }

    @Test fun `admin can access admin endpoint`() {
        mockMvc.perform(get("/api/test/admin").with(httpBasic("t_admin", "password")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Admin access granted - system-wide features available"))
            .andExpect(jsonPath("$.role").value("ADMIN"))
    }

    @Test fun `non-admin cannot access admin endpoint`() {
        mockMvc.perform(get("/api/test/admin").with(httpBasic("t_mgr", "password")))
            .andExpect(status().isForbidden)
    }

    @Test fun `branch manager can access their branch`() {
        mockMvc.perform(get("/api/test/manager/br_muscat_001").with(httpBasic("t_mgr", "password")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.branchId").value("br_muscat_001"))
    }

    @Test fun `branch manager cannot access other branch`() {
        mockMvc.perform(get("/api/test/manager/br_suhar_001").with(httpBasic("t_mgr", "password")))
            .andExpect(status().isForbidden)
    }

    @Test fun `admin can access any branch manager endpoint`() {
        mockMvc.perform(get("/api/test/manager/br_suhar_001").with(httpBasic("t_admin", "password")))
            .andExpect(status().isForbidden) // admin doesn't have BRANCH_MANAGER role
    }

    @Test fun `staff can access staff endpoint`() {
        mockMvc.perform(get("/api/test/staff").with(httpBasic("t_staff", "password")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Staff access granted - can view assigned appointments"))
    }

    @Test fun `staff cannot access slot creation endpoint`() {
        mockMvc.perform(post("/api/test/slots").with(httpBasic("t_staff", "password")))
            .andExpect(status().isForbidden)
    }

    @Test fun `admin can access slot creation endpoint`() {
        mockMvc.perform(post("/api/test/slots").with(httpBasic("t_admin", "password")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Slot creation access granted"))
    }

    @Test fun `manager can access slot creation endpoint`() {
        mockMvc.perform(post("/api/test/slots").with(httpBasic("t_mgr", "password")))
            .andExpect(status().isOk)
    }

    @Test fun `customer can access customer endpoint`() {
        mockMvc.perform(get("/api/test/customer").with(httpBasic("t_cust", "password")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Customer access granted - can manage own appointments"))
    }

    @Test fun `customer cannot access admin-only endpoint`() {
        mockMvc.perform(get("/api/test/admin-only").with(httpBasic("t_cust", "password")))
            .andExpect(status().isForbidden)
    }
}
