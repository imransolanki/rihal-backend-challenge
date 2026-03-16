package com.flowcare.backend.slot.controller

import com.flowcare.backend.auth.service.BranchAccessService
import com.flowcare.backend.slot.dto.*
import com.flowcare.backend.slot.service.SlotManagementService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/slots")
class SlotManagementController(
    private val slotManagementService: SlotManagementService,
    private val branchAccessService: BranchAccessService
) {

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun createSlot(@Valid @RequestBody request: CreateSlotRequest): ResponseEntity<SlotResponse> {
        validateBranchAccess(request.branchId)
        val response = slotManagementService.createSlot(request, branchAccessService.getCurrentUserId()!!, getCurrentRole())
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun createSlotsBulk(@Valid @RequestBody request: BulkCreateSlotsRequest): ResponseEntity<BulkCreateSlotsResponse> {
        request.slots.forEach { validateBranchAccess(it.branchId) }
        val response = slotManagementService.createSlotsBulk(request, branchAccessService.getCurrentUserId()!!, getCurrentRole())
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/{slotId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun updateSlot(@PathVariable slotId: String, @Valid @RequestBody request: UpdateSlotRequest): ResponseEntity<SlotResponse> {
        val slot = slotManagementService.getSlot(slotId, includeDeleted = true)
        validateBranchAccess(slot.branchId)
        val response = slotManagementService.updateSlot(slotId, request, branchAccessService.getCurrentUserId()!!, getCurrentRole())
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{slotId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun softDeleteSlot(@PathVariable slotId: String): ResponseEntity<SlotResponse> {
        val slot = slotManagementService.getSlot(slotId, includeDeleted = true)
        validateBranchAccess(slot.branchId)
        val response = slotManagementService.softDeleteSlot(slotId, branchAccessService.getCurrentUserId()!!, getCurrentRole())
        return ResponseEntity.ok(response)
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun listSlots(
        @RequestParam(required = false) branchId: String?,
        @RequestParam(required = false) serviceTypeId: String?,
        @RequestParam(required = false, defaultValue = "false") includeDeleted: Boolean
    ): ResponseEntity<List<SlotResponse>> {
        val effectiveBranchId = if (branchAccessService.isBranchManager()) branchAccessService.getCurrentUserBranchId() else branchId
        val effectiveIncludeDeleted = includeDeleted && branchAccessService.isAdmin()
        return ResponseEntity.ok(slotManagementService.listSlots(effectiveBranchId, serviceTypeId, effectiveIncludeDeleted))
    }

    @GetMapping("/{slotId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun getSlot(
        @PathVariable slotId: String,
        @RequestParam(required = false, defaultValue = "false") includeDeleted: Boolean
    ): ResponseEntity<SlotResponse> {
        val effectiveIncludeDeleted = includeDeleted && branchAccessService.isAdmin()
        val slot = slotManagementService.getSlot(slotId, effectiveIncludeDeleted)
        validateBranchAccess(slot.branchId)
        return ResponseEntity.ok(slot)
    }

    private fun validateBranchAccess(branchId: String) {
        if (!branchAccessService.hasAccessToBranch(branchId))
            throw AccessDeniedException("You do not have access to branch: $branchId")
    }

    private fun getCurrentRole(): String = when {
        branchAccessService.isAdmin() -> "ADMIN"
        branchAccessService.isBranchManager() -> "BRANCH_MANAGER"
        else -> "UNKNOWN"
    }
}
