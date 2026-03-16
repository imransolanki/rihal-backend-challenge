package com.flowcare.backend.branch.controller

import com.flowcare.backend.branch.model.Branch
import com.flowcare.backend.branch.repository.BranchRepository
import com.flowcare.backend.service.model.ServiceType
import com.flowcare.backend.service.repository.ServiceTypeRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class BranchControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var serviceTypeRepository: ServiceTypeRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    @BeforeEach
    fun setup() {
        serviceTypeRepository.deleteAll()
        branchRepository.deleteAll()
        cacheManager.cacheNames.forEach { cacheName ->
            cacheManager.getCache(cacheName)?.clear()
        }
    }

    @Test
    fun `GET all branches returns active branches only`() {
        branchRepository.save(Branch(
            id = "branch_001",
            name = "Muscat Branch",
            city = "Muscat",
            address = "123 Main St",
            isActive = true
        ))
        branchRepository.save(Branch(
            id = "branch_002",
            name = "Salalah Branch",
            city = "Salalah",
            address = "456 South St",
            isActive = false
        ))

        mockMvc.perform(get("/api/public/branches"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("branch_001"))
            .andExpect(jsonPath("$[0].name").value("Muscat Branch"))
    }

    @Test
    fun `GET all branches returns empty list when no active branches`() {
        mockMvc.perform(get("/api/public/branches"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET branch services returns branch with services`() {
        val branch = branchRepository.save(Branch(
            id = "branch_001",
            name = "Muscat Branch",
            city = "Muscat",
            address = "123 Main St",
            isActive = true
        ))
        
        serviceTypeRepository.save(ServiceType(
            id = "service_001",
            branchId = branch.id,
            name = "General Consultation",
            description = "Basic health checkup",
            durationMinutes = 30,
            isActive = true
        ))
        
        serviceTypeRepository.save(ServiceType(
            id = "service_002",
            branchId = branch.id,
            name = "Lab Test",
            description = "Blood work",
            durationMinutes = 15,
            isActive = true
        ))

        mockMvc.perform(get("/api/public/branches/branch_001/services"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.branch.id").value("branch_001"))
            .andExpect(jsonPath("$.branch.name").value("Muscat Branch"))
            .andExpect(jsonPath("$.services.length()").value(2))
            .andExpect(jsonPath("$.services[0].name").value("General Consultation"))
    }

    @Test
    fun `GET branch services returns 404 for non-existent branch`() {
        mockMvc.perform(get("/api/public/branches/invalid_branch/services"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET branch services returns empty services list when no services configured`() {
        branchRepository.save(Branch(
            id = "branch_002",
            name = "Empty Branch",
            city = "Salalah",
            address = "456 South St",
            isActive = true
        ))

        mockMvc.perform(get("/api/public/branches/branch_002/services"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.services.length()").value(0))
    }
}
