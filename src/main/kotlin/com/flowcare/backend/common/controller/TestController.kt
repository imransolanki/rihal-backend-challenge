package com.flowcare.backend.common.controller

import com.flowcare.backend.auth.model.UserPrincipal
import com.flowcare.backend.auth.service.BranchAccessService
import com.flowcare.backend.common.dto.AuthTestResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/test")
class TestController(private val branchAccessService: BranchAccessService) {

    @GetMapping("/protected")
    fun protectedEndpoint(@AuthenticationPrincipal p: UserPrincipal): ResponseEntity<AuthTestResponse> {
        val u = p.getUser()
        return ResponseEntity.ok(AuthTestResponse("Access granted to protected endpoint", u.id, u.username, u.role.name, u.branchId))
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun adminEndpoint(@AuthenticationPrincipal p: UserPrincipal): ResponseEntity<AuthTestResponse> {
        val u = p.getUser()
        return ResponseEntity.ok(AuthTestResponse("Admin access granted - system-wide features available", u.id, u.username, u.role.name, u.branchId))
    }

    @GetMapping("/manager/{branchId}")
    @PreAuthorize("hasRole('BRANCH_MANAGER') and @branchAccessService.hasAccessToBranch(#branchId)")
    fun managerBranchEndpoint(@PathVariable branchId: String, @AuthenticationPrincipal p: UserPrincipal): ResponseEntity<AuthTestResponse> {
        val u = p.getUser()
        return ResponseEntity.ok(AuthTestResponse("Branch Manager access granted to branch: $branchId", u.id, u.username, u.role.name, u.branchId))
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('STAFF')")
    fun staffEndpoint(@AuthenticationPrincipal p: UserPrincipal): ResponseEntity<AuthTestResponse> {
        val u = p.getUser()
        return ResponseEntity.ok(AuthTestResponse("Staff access granted - can view assigned appointments", u.id, u.username, u.role.name, u.branchId))
    }

    @PostMapping("/slots")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BRANCH_MANAGER')")
    fun createSlotEndpoint(@AuthenticationPrincipal p: UserPrincipal): ResponseEntity<AuthTestResponse> {
        val u = p.getUser()
        return ResponseEntity.ok(AuthTestResponse("Slot creation access granted", u.id, u.username, u.role.name, u.branchId))
    }

    @GetMapping("/customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    fun customerEndpoint(@AuthenticationPrincipal p: UserPrincipal): ResponseEntity<AuthTestResponse> {
        val u = p.getUser()
        return ResponseEntity.ok(AuthTestResponse("Customer access granted - can manage own appointments", u.id, u.username, u.role.name))
    }

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    fun adminOnlyEndpoint(@AuthenticationPrincipal p: UserPrincipal): ResponseEntity<AuthTestResponse> {
        val u = p.getUser()
        return ResponseEntity.ok(AuthTestResponse("Administrative feature access granted", u.id, u.username, u.role.name))
    }
}
