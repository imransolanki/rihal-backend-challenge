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
import org.springframework.cache.CacheManager
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

    @Autowired
    private lateinit var cacheManager: CacheManager

    @BeforeEach
    fun setup() {
        slotRepository.deleteAll()
        serviceTypeRepository.deleteAll()
        branchRepository.deleteAll()
        userRepository.deleteAll()
        cacheManager.getCache("availableSlots")?.clear()
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
            .andExpect(jsonPath("$[0].availableCapacity").value(1))
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
