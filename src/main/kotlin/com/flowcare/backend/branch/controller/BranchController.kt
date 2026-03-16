package com.flowcare.backend.branch.controller

import com.flowcare.backend.branch.dto.BranchResponse
import com.flowcare.backend.branch.service.BranchService
import com.flowcare.backend.service.dto.BranchWithServicesResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/public/branches")
class BranchController(
    private val branchService: BranchService
) {

    @GetMapping
    fun getAllBranches(): ResponseEntity<List<BranchResponse>> {
        val branches = branchService.getAllActiveBranches()
        return ResponseEntity.ok(branches)
    }
    
    @GetMapping("/{branchId}/services")
    fun getBranchServices(@PathVariable branchId: String): ResponseEntity<BranchWithServicesResponse> {
        val result = branchService.getBranchWithServices(branchId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }
}
