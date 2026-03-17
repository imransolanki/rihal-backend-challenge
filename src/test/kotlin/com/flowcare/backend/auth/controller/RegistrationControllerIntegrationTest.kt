package com.flowcare.backend.auth.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun idDocumentFile() = MockMultipartFile(
        "idDocument", "id.jpg", "image/jpeg", "fake-image-content".toByteArray()
    )

    private fun registerUser(username: String, email: String) {
        mockMvc.perform(
            multipart("/api/auth/register")
                .file(idDocumentFile())
                .param("username", username)
                .param("password", "Password@123")
                .param("fullName", "Test User")
                .param("email", email)
                .param("phone", "+96899887766")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `should register customer with valid data and ID document`() {
        mockMvc.perform(
            multipart("/api/auth/register")
                .file(idDocumentFile())
                .param("username", "newcustomer")
                .param("password", "Password@123")
                .param("fullName", "New Customer")
                .param("email", "newcustomer@test.com")
                .param("phone", "+96899887766")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.username").value("newcustomer"))
            .andExpect(jsonPath("$.email").value("newcustomer@test.com"))
            .andExpect(jsonPath("$.fullName").value("New Customer"))
    }

    @Test
    fun `should reject duplicate username`() {
        registerUser("dupuser", "first@test.com")

        mockMvc.perform(
            multipart("/api/auth/register")
                .file(idDocumentFile())
                .param("username", "dupuser")
                .param("password", "Password@123")
                .param("fullName", "Duplicate User")
                .param("email", "second@test.com")
                .param("phone", "+96899887766")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `should reject duplicate email`() {
        registerUser("firstuser", "same@test.com")

        mockMvc.perform(
            multipart("/api/auth/register")
                .file(idDocumentFile())
                .param("username", "seconduser")
                .param("password", "Password@123")
                .param("fullName", "Unique User")
                .param("email", "same@test.com")
                .param("phone", "+96899887766")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `should reject registration without ID document`() {
        mockMvc.perform(
            multipart("/api/auth/register")
                .param("username", "noiddoc")
                .param("password", "Password@123")
                .param("fullName", "No ID Doc")
                .param("email", "noiddoc@test.com")
                .param("phone", "+96899887766")
        )
            .andExpect(status().isBadRequest)
    }
}
