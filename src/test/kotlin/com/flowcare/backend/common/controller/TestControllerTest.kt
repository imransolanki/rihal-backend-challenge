package com.flowcare.backend.common.controller

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import com.flowcare.backend.auth.repository.UserRepository
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

@SpringBootTest
@AutoConfigureMockMvc
class TestControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun setup() {
        userRepository.deleteAll()
        userRepository.save(
            User(
                id = "test_admin",
                username = "admin",
                password = passwordEncoder.encode("password"),
                role = Role.ADMIN,
                fullName = "Admin User",
                email = "admin@test.com"
            )
        )
    }

    @Test
    fun `should deny access without authentication`() {
        mockMvc.perform(get("/api/test/protected"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `should allow access with valid credentials`() {
        mockMvc.perform(
            get("/api/test/protected")
                .with(httpBasic("admin", "password"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Access granted to protected endpoint"))
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.role").value("ADMIN"))
    }

    @Test
    fun `should deny access with invalid credentials`() {
        mockMvc.perform(
            get("/api/test/protected")
                .with(httpBasic("admin", "wrongpassword"))
        )
            .andExpect(status().isUnauthorized)
    }
}
