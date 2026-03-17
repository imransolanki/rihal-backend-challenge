package com.flowcare.backend.staff.service

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.branch.repository.BranchRepository
import com.flowcare.backend.service.repository.ServiceTypeRepository
import com.flowcare.backend.staff.dto.ServiceAssignmentDto
import com.flowcare.backend.staff.dto.StaffDetailResponse
import com.flowcare.backend.staff.dto.StaffListResponse
import com.flowcare.backend.staff.repository.StaffServiceTypeRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class StaffService(
    private val userRepository: UserRepository,
    private val branchRepository: BranchRepository,
    private val staffServiceTypeRepository: StaffServiceTypeRepository,
    private val serviceTypeRepository: ServiceTypeRepository
) {
    
    fun listAllStaff(pageable: Pageable): Page<StaffListResponse> {
        return userRepository.findByRole(Role.STAFF, pageable)
            .map { user ->
                val branch = branchRepository.findById(user.branchId!!)
                    .orElseThrow { IllegalStateException("Branch not found for staff") }
                
                StaffListResponse(
                    id = user.id,
                    fullName = user.fullName,
                    email = user.email,
                    branchId = user.branchId!!,
                    branchName = branch.name,
                    isActive = user.isActive
                )
            }
    }
    
    fun listStaffByBranch(branchId: String, pageable: Pageable): Page<StaffListResponse> {
        return userRepository.findByRoleAndBranchId(Role.STAFF, branchId, pageable)
            .map { user ->
                val branch = branchRepository.findById(user.branchId!!)
                    .orElseThrow { IllegalStateException("Branch not found") }
                
                StaffListResponse(
                    id = user.id,
                    fullName = user.fullName,
                    email = user.email,
                    branchId = user.branchId!!,
                    branchName = branch.name,
                    isActive = user.isActive
                )
            }
    }
    
    fun getStaffDetail(staffId: String): StaffDetailResponse {
        val user = userRepository.findById(staffId)
            .orElseThrow { IllegalArgumentException("Staff not found") }
        
        if (user.role != Role.STAFF) {
            throw IllegalArgumentException("User is not a staff member")
        }
        
        val branch = branchRepository.findById(user.branchId!!)
            .orElseThrow { IllegalStateException("Branch not found") }
        
        val assignments = staffServiceTypeRepository.findByStaffId(staffId)
        val serviceAssignments = assignments.map { assignment ->
            val serviceType = serviceTypeRepository.findById(assignment.serviceTypeId)
                .orElseThrow { IllegalStateException("Service type not found") }
            
            ServiceAssignmentDto(
                serviceTypeId = serviceType.id,
                serviceTypeName = serviceType.name
            )
        }
        
        return StaffDetailResponse(
            id = user.id,
            fullName = user.fullName,
            email = user.email,
            phone = user.phone,
            branchId = user.branchId!!,
            branchName = branch.name,
            isActive = user.isActive,
            serviceAssignments = serviceAssignments
        )
    }
}
