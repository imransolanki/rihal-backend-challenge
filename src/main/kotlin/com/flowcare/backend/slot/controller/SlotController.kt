package com.flowcare.backend.slot.controller

import com.flowcare.backend.slot.dto.AvailableSlotResponse
import com.flowcare.backend.slot.service.SlotService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/public/slots")
class SlotController(
    private val slotService: SlotService
) {

    @GetMapping
    fun getAvailableSlots(
        @RequestParam branchId: String,
        @RequestParam serviceTypeId: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?
    ): ResponseEntity<List<AvailableSlotResponse>> {
        val slots = slotService.getAvailableSlots(branchId, serviceTypeId, date)
        return ResponseEntity.ok(slots)
    }
}
