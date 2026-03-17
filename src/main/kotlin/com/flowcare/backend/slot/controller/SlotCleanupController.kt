package com.flowcare.backend.slot.controller

import com.flowcare.backend.auth.service.BranchAccessService
import com.flowcare.backend.slot.dto.CleanupSummaryResponse
import com.flowcare.backend.slot.service.SlotCleanupService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/slots")
class SlotCleanupController(
    private val cleanupService: SlotCleanupService,
    private val branchAccessService: BranchAccessService
) {
    
    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    fun executeCleanup(
        @AuthenticationPrincipal userDetails: UserDetails
    ): CleanupSummaryResponse {
        val user = branchAccessService.getCurrentUser(userDetails.username)
        return cleanupService.executeCleanup(user.id, user.role.name)
    }
}
