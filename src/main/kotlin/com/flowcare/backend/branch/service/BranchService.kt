package com.flowcare.backend.branch.service

import com.flowcare.backend.branch.dto.BranchResponse
import com.flowcare.backend.branch.repository.BranchRepository
import com.flowcare.backend.service.dto.BranchWithServicesResponse
import com.flowcare.backend.service.service.ServiceTypeService
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class BranchService(
    private val branchRepository: BranchRepository,
    private val serviceTypeService: ServiceTypeService
) {
    
    @Cacheable(value = ["branches"], key = "'all'")
    fun getAllActiveBranches(): List<BranchResponse> {
        return branchRepository.findAll()
            .filter { it.isActive }
            .map { branch ->
                BranchResponse(
                    id = branch.id,
                    name = branch.name,
                    city = branch.city,
                    address = branch.address,
                    timezone = branch.timezone
                )
            }
    }
    
    @Cacheable(value = ["branchWithServices"], key = "#branchId")
    fun getBranchWithServices(branchId: String): BranchWithServicesResponse? {
        val branch = branchRepository.findById(branchId).orElse(null) ?: return null
        
        if (!branch.isActive) return null
        
        val branchResponse = BranchResponse(
            id = branch.id,
            name = branch.name,
            city = branch.city,
            address = branch.address,
            timezone = branch.timezone
        )
        
        val services = serviceTypeService.getServicesByBranch(branchId)
        
        return BranchWithServicesResponse(
            branch = branchResponse,
            services = services
        )
    }
}
