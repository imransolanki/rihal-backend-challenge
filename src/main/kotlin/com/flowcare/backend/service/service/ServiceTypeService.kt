package com.flowcare.backend.service.service

import com.flowcare.backend.service.dto.ServiceTypeResponse
import com.flowcare.backend.service.repository.ServiceTypeRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class ServiceTypeService(
    private val serviceTypeRepository: ServiceTypeRepository
) {
    
    @Cacheable(value = ["services"], key = "#branchId")
    fun getServicesByBranch(branchId: String): List<ServiceTypeResponse> {
        return serviceTypeRepository.findAll()
            .filter { it.branchId == branchId && it.isActive }
            .map { service ->
                ServiceTypeResponse(
                    id = service.id,
                    name = service.name,
                    description = service.description,
                    durationMinutes = service.durationMinutes
                )
            }
    }
}
