package com.flowcare.backend.slot.service

import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.slot.dto.AvailableSlotResponse
import com.flowcare.backend.slot.dto.StaffBasicInfo
import com.flowcare.backend.slot.repository.SlotRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
class SlotService(
    private val slotRepository: SlotRepository,
    private val userRepository: UserRepository
) {
    
    @Cacheable(value = ["availableSlots"], key = "#branchId + '_' + #serviceTypeId + '_' + (#date?.toString() ?: 'all')")
    fun getAvailableSlots(
        branchId: String,
        serviceTypeId: String,
        date: LocalDate?
    ): List<AvailableSlotResponse> {
        val now = OffsetDateTime.now()
        
        val slots = if (date != null) {
            val dateTime = date.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime()
            slotRepository.findAvailableSlotsByDate(branchId, serviceTypeId, dateTime, now)
        } else {
            slotRepository.findAvailableSlots(branchId, serviceTypeId, now)
        }
        
        val staffIds = slots.mapNotNull { it.staffId }.distinct()
        val staffMap = if (staffIds.isNotEmpty()) {
            userRepository.findAllById(staffIds).associateBy { it.id }
        } else {
            emptyMap()
        }
        
        return slots.map { slot ->
            AvailableSlotResponse(
                id = slot.id,
                branchId = slot.branchId,
                serviceTypeId = slot.serviceTypeId,
                startAt = slot.startAt,
                endAt = slot.endAt,
                capacity = slot.capacity,
                availableCapacity = slot.capacity - slot.bookedCount,
                staff = slot.staffId?.let { staffId ->
                    staffMap[staffId]?.let { user ->
                        StaffBasicInfo(id = user.id, fullName = user.fullName)
                    }
                }
            )
        }
    }
}
