package com.flowcare.backend.slot.service

import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.branch.repository.BranchRepository
import com.flowcare.backend.service.repository.ServiceTypeRepository
import com.flowcare.backend.slot.dto.CreateSlotRequest
import com.flowcare.backend.slot.exception.SlotValidationException
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class SlotValidationService(
    private val branchRepository: BranchRepository,
    private val serviceTypeRepository: ServiceTypeRepository,
    private val userRepository: UserRepository
) {

    fun validateSlotCreation(request: CreateSlotRequest) {
        if (!branchRepository.existsById(request.branchId))
            throw SlotValidationException("Branch not found: ${request.branchId}")

        if (!serviceTypeRepository.existsById(request.serviceTypeId))
            throw SlotValidationException("Service type not found: ${request.serviceTypeId}")

        request.staffId?.let { staffId ->
            val staff = userRepository.findById(staffId)
                .orElseThrow { SlotValidationException("Staff not found: $staffId") }
            if (staff.branchId != request.branchId)
                throw SlotValidationException("Staff does not belong to branch ${request.branchId}")
        }

        if (request.startAt.isAfter(request.endAt) || request.startAt.isEqual(request.endAt))
            throw SlotValidationException("Start time must be before end time")

        if (request.startAt.isBefore(OffsetDateTime.now()))
            throw SlotValidationException("Cannot create slot in the past")
    }

    fun validateSlotCreationSafe(request: CreateSlotRequest): String? {
        return try {
            validateSlotCreation(request)
            null
        } catch (e: SlotValidationException) {
            e.message
        }
    }
}
