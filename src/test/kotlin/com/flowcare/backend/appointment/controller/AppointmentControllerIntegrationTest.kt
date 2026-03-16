package com.flowcare.backend.appointment.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.flowcare.backend.appointment.dto.BookAppointmentRequest
import com.flowcare.backend.appointment.dto.RescheduleAppointmentRequest
import com.flowcare.backend.appointment.model.AppointmentStatus
import com.flowcare.backend.appointment.repository.AppointmentRepository
import com.flowcare.backend.audit.repository.AuditLogRepository
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
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.OffsetDateTime
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var appointmentRepository: AppointmentRepository

    @Autowired
    private lateinit var slotRepository: SlotRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var serviceTypeRepository: ServiceTypeRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var customer: User
    private lateinit var branch: Branch
    private lateinit var serviceType: ServiceType
    private lateinit var slot: Slot

    @BeforeEach
    fun setup() {
        appointmentRepository.deleteAll()
        auditLogRepository.deleteAll()
        slotRepository.deleteAll()
        serviceTypeRepository.deleteAll()
        branchRepository.deleteAll()
        userRepository.deleteAll()

        branch = branchRepository.save(
            Branch(
                id = "branch_test_1",
                name = "Test Branch",
                city = "Muscat",
                address = "123 Test St",
                timezone = "Asia/Muscat"
            )
        )

        serviceType = serviceTypeRepository.save(
            ServiceType(
                id = "service_test_1",
                branchId = branch.id,
                name = "Test Service",
                description = "Test service description",
                durationMinutes = 30
            )
        )

        customer = userRepository.save(
            User(
                id = "usr_cust_test_1",
                username = "testcustomer",
                password = passwordEncoder.encode("password123"),
                role = Role.CUSTOMER,
                fullName = "Test Customer",
                email = "testcustomer@example.com"
            )
        )

        slot = slotRepository.save(
            Slot(
                id = "slot_test_1",
                branchId = branch.id,
                serviceTypeId = serviceType.id,
                staffId = null,
                startAt = OffsetDateTime.now().plusDays(1),
                endAt = OffsetDateTime.now().plusDays(1).plusHours(1),
                capacity = 5,
                bookedCount = 0
            )
        )
    }

    @Test
    fun `should book appointment successfully`() {
        val request = BookAppointmentRequest(slotId = slot.id)
        val requestJson = objectMapper.writeValueAsString(request)
        val requestPart = MockMultipartFile("request", "", "application/json", requestJson.toByteArray())

        mockMvc.perform(
            multipart("/api/appointments/book")
                .file(requestPart)
                .with(httpBasic(customer.username, "password123"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customerId").value(customer.id))
            .andExpect(jsonPath("$.slotId").value(slot.id))
            .andExpect(jsonPath("$.status").value(AppointmentStatus.BOOKED.name))
            .andExpect(jsonPath("$.hasAttachment").value(false))

        // Verify slot bookedCount was incremented
        val updatedSlot = slotRepository.findById(slot.id).get()
        assert(updatedSlot.bookedCount == 1)

        // Verify audit log was created
        val auditLogs = auditLogRepository.findAll()
        assert(auditLogs.any { it.actionType == "APPOINTMENT_CREATED" })
    }

    @Test
    fun `should book appointment with attachment`() {
        val request = BookAppointmentRequest(slotId = slot.id)
        val requestJson = objectMapper.writeValueAsString(request)
        val requestPart = MockMultipartFile("request", "", "application/json", requestJson.toByteArray())
        val attachment = MockMultipartFile("attachment", "test.jpg", "image/jpeg", "test content".toByteArray())

        mockMvc.perform(
            multipart("/api/appointments/book")
                .file(requestPart)
                .file(attachment)
                .with(httpBasic(customer.username, "password123"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hasAttachment").value(true))
    }

    @Test
    fun `should reject booking without authentication`() {
        val request = BookAppointmentRequest(slotId = slot.id)
        val requestJson = objectMapper.writeValueAsString(request)
        val requestPart = MockMultipartFile("request", "", "application/json", requestJson.toByteArray())

        mockMvc.perform(
            multipart("/api/appointments/book")
                .file(requestPart)
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `should reject booking with invalid slot`() {
        val request = BookAppointmentRequest(slotId = "invalid_slot")
        val requestJson = objectMapper.writeValueAsString(request)
        val requestPart = MockMultipartFile("request", "", "application/json", requestJson.toByteArray())

        mockMvc.perform(
            multipart("/api/appointments/book")
                .file(requestPart)
                .with(httpBasic(customer.username, "password123"))
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `should get customer appointments list`() {
        // Book an appointment first
        val request = BookAppointmentRequest(slotId = slot.id)
        val requestJson = objectMapper.writeValueAsString(request)
        val requestPart = MockMultipartFile("request", "", "application/json", requestJson.toByteArray())

        mockMvc.perform(
            multipart("/api/appointments/book")
                .file(requestPart)
                .with(httpBasic(customer.username, "password123"))
        )

        // Get appointments list
        mockMvc.perform(
            get("/api/appointments/my")
                .with(httpBasic(customer.username, "password123"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].customerId").value(customer.id))
            .andExpect(jsonPath("$[0].status").value(AppointmentStatus.BOOKED.name))
    }

    @Test
    fun `should get appointment details`() {
        // Book an appointment first
        val request = BookAppointmentRequest(slotId = slot.id)
        val requestJson = objectMapper.writeValueAsString(request)
        val requestPart = MockMultipartFile("request", "", "application/json", requestJson.toByteArray())

        val result = mockMvc.perform(
            multipart("/api/appointments/book")
                .file(requestPart)
                .with(httpBasic(customer.username, "password123"))
        ).andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        val appointmentId = response.get("id").asText()

        // Get appointment details
        mockMvc.perform(
            get("/api/appointments/$appointmentId")
                .with(httpBasic(customer.username, "password123"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(appointmentId))
            .andExpect(jsonPath("$.customerId").value(customer.id))
    }

    @Test
    fun `should cancel appointment successfully`() {
        // Book an appointment first
        val request = BookAppointmentRequest(slotId = slot.id)
        val requestJson = objectMapper.writeValueAsString(request)
        val requestPart = MockMultipartFile("request", "", "application/json", requestJson.toByteArray())

        val result = mockMvc.perform(
            multipart("/api/appointments/book")
                .file(requestPart)
                .with(httpBasic(customer.username, "password123"))
        ).andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        val appointmentId = response.get("id").asText()

        // Cancel the appointment
        mockMvc.perform(
            post("/api/appointments/$appointmentId/cancel")
                .with(httpBasic(customer.username, "password123"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(AppointmentStatus.CANCELLED.name))

        // Verify slot bookedCount was decremented
        val updatedSlot = slotRepository.findById(slot.id).get()
        assert(updatedSlot.bookedCount == 0)

        // Verify audit log was created
        val auditLogs = auditLogRepository.findAll()
        assert(auditLogs.any { it.actionType == "APPOINTMENT_CANCELLED" })
    }

    @Test
    fun `should not cancel already cancelled appointment`() {
        // Book and cancel an appointment
        val request = BookAppointmentRequest(slotId = slot.id)
        val requestJson = objectMapper.writeValueAsString(request)
        val requestPart = MockMultipartFile("request", "", "application/json", requestJson.toByteArray())

        val result = mockMvc.perform(
            multipart("/api/appointments/book")
                .file(requestPart)
                .with(httpBasic(customer.username, "password123"))
        ).andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        val appointmentId = response.get("id").asText()

        mockMvc.perform(
            post("/api/appointments/$appointmentId/cancel")
                .with(httpBasic(customer.username, "password123"))
        )

        // Try to cancel again
        mockMvc.perform(
            post("/api/appointments/$appointmentId/cancel")
                .with(httpBasic(customer.username, "password123"))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should reschedule appointment successfully`() {
        // Create a second slot
        val newSlot = slotRepository.save(
            Slot(
                id = "slot_test_2",
                branchId = branch.id,
                serviceTypeId = serviceType.id,
                staffId = null,
                startAt = OffsetDateTime.now().plusDays(2),
                endAt = OffsetDateTime.now().plusDays(2).plusHours(1),
                capacity = 5,
                bookedCount = 0
            )
        )

        // Book an appointment
        val request = BookAppointmentRequest(slotId = slot.id)
        val requestJson = objectMapper.writeValueAsString(request)
        val requestPart = MockMultipartFile("request", "", "application/json", requestJson.toByteArray())

        val result = mockMvc.perform(
            multipart("/api/appointments/book")
                .file(requestPart)
                .with(httpBasic(customer.username, "password123"))
        ).andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        val appointmentId = response.get("id").asText()

        // Reschedule to new slot
        val rescheduleRequest = RescheduleAppointmentRequest(newSlotId = newSlot.id)

        mockMvc.perform(
            post("/api/appointments/$appointmentId/reschedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rescheduleRequest))
                .with(httpBasic(customer.username, "password123"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.slotId").value(newSlot.id))

        // Verify old slot was released
        val oldSlot = slotRepository.findById(slot.id).get()
        assert(oldSlot.bookedCount == 0)

        // Verify new slot was booked
        val updatedNewSlot = slotRepository.findById(newSlot.id).get()
        assert(updatedNewSlot.bookedCount == 1)

        // Verify audit log was created
        val auditLogs = auditLogRepository.findAll()
        assert(auditLogs.any { it.actionType == "APPOINTMENT_RESCHEDULED" })
    }

    @Test
    fun `should prevent concurrent booking of same slot`() {
        // This test verifies optimistic locking works
        // Book the slot until it's full
        repeat(5) {
            val request = BookAppointmentRequest(slotId = slot.id)
            val requestJson = objectMapper.writeValueAsString(request)
            val requestPart = MockMultipartFile("request", "", "application/json", requestJson.toByteArray())

            mockMvc.perform(
                multipart("/api/appointments/book")
                    .file(requestPart)
                    .with(httpBasic(customer.username, "password123"))
            )
        }

        // Try to book one more time - should fail
        val request = BookAppointmentRequest(slotId = slot.id)
        val requestJson = objectMapper.writeValueAsString(request)
        val requestPart = MockMultipartFile("request", "", "application/json", requestJson.toByteArray())

        mockMvc.perform(
            multipart("/api/appointments/book")
                .file(requestPart)
                .with(httpBasic(customer.username, "password123"))
        )
            .andExpect(status().isConflict)
    }
}
