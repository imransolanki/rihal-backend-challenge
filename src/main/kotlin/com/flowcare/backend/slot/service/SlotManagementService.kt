package com.flowcare.backend.slot.service

import com.flowcare.backend.audit.service.AuditLogService
import com.flowcare.backend.slot.dto.*
import com.flowcare.backend.slot.exception.SlotAlreadyDeletedException
import com.flowcare.backend.slot.exception.SlotHasActiveBookingsException
import com.flowcare.backend.slot.exception.SlotValidationException
import com.flowcare.backend.slot.model.Slot
import com.flowcare.backend.slot.repository.SlotRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Service
class SlotManagementService(
    private val slotRepository: SlotRepository,
    private val slotValidationService: SlotValidationService,
    private val auditLogService: AuditLogService
) {

    @Transactional
    @CacheEvict(value = ["availableSlots"], allEntries = true)
    fun createSlot(request: CreateSlotRequest, actorId: String, actorRole: String): SlotResponse {
        slotValidationService.validateSlotCreation(request)

        val slot = Slot(
            id = "slot_${UUID.randomUUID()}",
            branchId = request.branchId,
            serviceTypeId = request.serviceTypeId,
            staffId = request.staffId,
            startAt = request.startAt,
            endAt = request.endAt,
            capacity = request.capacity
        )
        val saved = slotRepository.save(slot)

        auditLogService.log(
            actorId = actorId, actorRole = actorRole,
            actionType = "SLOT_CREATED", entityType = "Slot", entityId = saved.id,
            metadata = mapOf(
                "branchId" to saved.branchId, "serviceTypeId" to saved.serviceTypeId,
                "staffId" to (saved.staffId ?: "unassigned"),
                "startAt" to saved.startAt.toString(), "endAt" to saved.endAt.toString(),
                "capacity" to saved.capacity
            )
        )
        return saved.toResponse()
    }

    @Transactional
    @CacheEvict(value = ["availableSlots"], allEntries = true)
    fun createSlotsBulk(request: BulkCreateSlotsRequest, actorId: String, actorRole: String): BulkCreateSlotsResponse {
        val createdSlots = mutableListOf<SlotResponse>()
        val failures = mutableListOf<SlotCreationFailure>()

        request.slots.forEachIndexed { index, slotRequest ->
            val error = slotValidationService.validateSlotCreationSafe(slotRequest)
            if (error != null) {
                failures.add(SlotCreationFailure(index, slotRequest, error))
            } else {
                try {
                    val slot = slotRepository.save(Slot(
                        id = "slot_${UUID.randomUUID()}",
                        branchId = slotRequest.branchId, serviceTypeId = slotRequest.serviceTypeId,
                        staffId = slotRequest.staffId, startAt = slotRequest.startAt,
                        endAt = slotRequest.endAt, capacity = slotRequest.capacity
                    ))
                    createdSlots.add(slot.toResponse())
                } catch (e: Exception) {
                    failures.add(SlotCreationFailure(index, slotRequest, e.message ?: "Unknown error"))
                }
            }
        }

        if (createdSlots.isNotEmpty()) {
            auditLogService.log(
                actorId = actorId, actorRole = actorRole,
                actionType = "SLOT_CREATED", entityType = "Slot", entityId = "bulk_operation",
                metadata = mapOf(
                    "bulkCreation" to true, "totalRequested" to request.slots.size,
                    "successCount" to createdSlots.size, "failureCount" to failures.size,
                    "slotIds" to createdSlots.map { it.id }
                )
            )
        }

        return BulkCreateSlotsResponse(createdSlots.size, failures.size, createdSlots, failures)
    }

    @Transactional
    @CacheEvict(value = ["availableSlots"], allEntries = true)
    fun updateSlot(slotId: String, request: UpdateSlotRequest, actorId: String, actorRole: String): SlotResponse {
        val slot = slotRepository.findById(slotId)
            .orElseThrow { SlotValidationException("Slot not found: $slotId") }

        if (slot.deletedAt != null) throw SlotAlreadyDeletedException("Cannot update deleted slot: $slotId")

        val isBooked = slot.bookedCount > 0
        if (isBooked && (request.startAt != null || request.endAt != null || request.capacity != null))
            throw SlotValidationException("Cannot change time or capacity of booked slot")

        val oldValues = mutableMapOf<String, Any?>()
        val newValues = mutableMapOf<String, Any?>()

        val updatedSlot = slot.copy(
            startAt = if (!isBooked && request.startAt != null) {
                oldValues["startAt"] = slot.startAt.toString(); newValues["startAt"] = request.startAt.toString()
                request.startAt
            } else slot.startAt,
            endAt = if (!isBooked && request.endAt != null) {
                oldValues["endAt"] = slot.endAt.toString(); newValues["endAt"] = request.endAt.toString()
                request.endAt
            } else slot.endAt,
            staffId = when {
                request.removeStaffAssignment -> {
                    oldValues["staffId"] = slot.staffId ?: "unassigned"; newValues["staffId"] = "unassigned"; null
                }
                request.staffId != null -> {
                    oldValues["staffId"] = slot.staffId ?: "unassigned"; newValues["staffId"] = request.staffId
                    request.staffId
                }
                else -> slot.staffId
            },
            capacity = if (!isBooked && request.capacity != null) {
                if (request.capacity < slot.bookedCount)
                    throw SlotValidationException("Cannot reduce capacity below booked count")
                oldValues["capacity"] = slot.capacity; newValues["capacity"] = request.capacity
                request.capacity
            } else slot.capacity,
            updatedAt = LocalDateTime.now()
        )

        val saved = slotRepository.save(updatedSlot)

        if (oldValues.isNotEmpty()) {
            auditLogService.log(
                actorId = actorId, actorRole = actorRole,
                actionType = "SLOT_UPDATED", entityType = "Slot", entityId = saved.id,
                metadata = mapOf("branchId" to saved.branchId, "oldValues" to oldValues, "newValues" to newValues)
            )
        }
        return saved.toResponse()
    }

    @Transactional
    @CacheEvict(value = ["availableSlots"], allEntries = true)
    fun softDeleteSlot(slotId: String, actorId: String, actorRole: String): SlotResponse {
        val slot = slotRepository.findById(slotId)
            .orElseThrow { SlotValidationException("Slot not found: $slotId") }

        if (slot.deletedAt != null) throw SlotAlreadyDeletedException("Slot already deleted: $slotId")
        if (slot.bookedCount > 0) throw SlotHasActiveBookingsException("Cannot delete slot with active bookings: $slotId")

        val deletedSlot = slot.copy(deletedAt = OffsetDateTime.now(), updatedAt = LocalDateTime.now())
        val saved = slotRepository.save(deletedSlot)

        auditLogService.log(
            actorId = actorId, actorRole = actorRole,
            actionType = "SLOT_SOFT_DELETED", entityType = "Slot", entityId = saved.id,
            metadata = mapOf(
                "branchId" to saved.branchId, "serviceTypeId" to saved.serviceTypeId,
                "staffId" to (saved.staffId ?: "unassigned"), "deletedAt" to saved.deletedAt.toString()
            )
        )
        return saved.toResponse()
    }

    fun getSlot(slotId: String, includeDeleted: Boolean = false): SlotResponse {
        val slot = slotRepository.findById(slotId)
            .orElseThrow { SlotValidationException("Slot not found: $slotId") }
        if (!includeDeleted && slot.deletedAt != null) throw SlotValidationException("Slot not found: $slotId")
        return slot.toResponse()
    }

    fun listSlots(branchId: String?, serviceTypeId: String?, includeDeleted: Boolean = false): List<SlotResponse> {
        val slots = when {
            branchId != null && serviceTypeId != null -> slotRepository.findByBranchIdAndServiceTypeId(branchId, serviceTypeId)
            branchId != null -> slotRepository.findByBranchId(branchId)
            else -> slotRepository.findAll()
        }
        return slots.filter { includeDeleted || it.deletedAt == null }.map { it.toResponse() }
    }

    private fun Slot.toResponse() = SlotResponse(
        id = id, branchId = branchId, serviceTypeId = serviceTypeId, staffId = staffId,
        startAt = startAt, endAt = endAt, capacity = capacity, bookedCount = bookedCount,
        isActive = isActive, deletedAt = deletedAt, createdAt = createdAt, updatedAt = updatedAt
    )
}
