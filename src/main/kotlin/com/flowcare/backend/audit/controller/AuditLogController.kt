package com.flowcare.backend.audit.controller

import com.flowcare.backend.audit.dto.AuditLogResponse
import com.flowcare.backend.audit.service.AuditLogQueryService
import com.flowcare.backend.auth.service.BranchAccessService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/audit-logs")
class AuditLogController(
    private val auditLogQueryService: AuditLogQueryService,
    private val branchAccessService: BranchAccessService
) {
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun listAuditLogs(
        @AuthenticationPrincipal userDetails: UserDetails,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        pageable: Pageable
    ): Page<AuditLogResponse> {
        val user = branchAccessService.getCurrentUser(userDetails.username)
        
        return if (branchAccessService.isAdmin(user)) {
            auditLogQueryService.listAllLogs(startDate, endDate, pageable)
        } else {
            val branchId = branchAccessService.getUserBranchId(user)
            auditLogQueryService.listLogsByBranch(branchId, startDate, endDate, pageable)
        }
    }
}
