package com.flowcare.backend.auth.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `should allow unauthenticated access to health endpoint`() {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk)
    }

    @Test
    fun `should allow unauthenticated access to public slots endpoint`() {
        mockMvc.perform(get("/api/public/slots").param("branchId", "br_muscat_001").param("serviceTypeId", "svc_001"))
            .andExpect(status().isOk)
    }

    @Test
    fun `should reject unauthenticated access to protected endpoint`() {
        mockMvc.perform(get("/api/staff")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `should reject customer access to admin-only endpoint`() {
        mockMvc.perform(get("/api/staff").with(user("customer").roles("CUSTOMER")))
            .andExpect(status().isForbidden)
    }
}
