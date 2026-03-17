package com.flowcare.backend.staff.controller

import com.flowcare.backend.auth.service.BranchAccessService
import com.flowcare.backend.staff.dto.StaffDetailResponse
import com.flowcare.backend.staff.dto.StaffListResponse
import com.flowcare.backend.staff.service.StaffService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/staff")
class StaffController(
    private val staffService: StaffService,
    private val branchAccessService: BranchAccessService
) {
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun listStaff(
        @AuthenticationPrincipal userDetails: UserDetails,
        pageable: Pageable
    ): Page<StaffListResponse> {
        val user = branchAccessService.getCurrentUser(userDetails.username)
        
        return if (branchAccessService.isAdmin(user)) {
            staffService.listAllStaff(pageable)
        } else {
            val branchId = branchAccessService.getUserBranchId(user)
            staffService.listStaffByBranch(branchId, pageable)
        }
    }
    
    @GetMapping("/{staffId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun getStaffDetail(
        @PathVariable staffId: String,
        @AuthenticationPrincipal userDetails: UserDetails
    ): StaffDetailResponse {
        val user = branchAccessService.getCurrentUser(userDetails.username)
        val staffDetail = staffService.getStaffDetail(staffId)
        
        // Branch Manager can only view staff in their branch
        if (!branchAccessService.isAdmin(user)) {
            val branchId = branchAccessService.getUserBranchId(user)
            if (staffDetail.branchId != branchId) {
                throw IllegalAccessException("Cannot access staff from another branch")
            }
        }
        
        return staffDetail
    }
}
