package com.flowcare.backend.slot.service

import com.flowcare.backend.appointment.repository.AppointmentRepository
import com.flowcare.backend.audit.service.AuditLogService
import com.flowcare.backend.config.service.SystemConfigService
import com.flowcare.backend.slot.dto.CleanupSummaryResponse
import com.flowcare.backend.slot.repository.SlotRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
class SlotCleanupService(
    private val slotRepository: SlotRepository,
    private val appointmentRepository: AppointmentRepository,
    private val configService: SystemConfigService,
    private val auditLogService: AuditLogService
) {
    private val log = LoggerFactory.getLogger(SlotCleanupService::class.java)
    
    @Transactional
    fun executeCleanup(actorId: String, actorRole: String): CleanupSummaryResponse {
        val retentionConfig = configService.getRetentionPeriod()
        val cutoffDate = OffsetDateTime.now().minusDays(retentionConfig.retentionPeriodDays.toLong())
        
        log.info("Starting cleanup for slots deleted before: {}", cutoffDate)
        
        val slotsToDelete = slotRepository.findSoftDeletedBeforeDate(cutoffDate)
        
        // Filter out slots that have appointments
        val eligibleSlots = slotsToDelete.filter { slot ->
            val hasAppointments = appointmentRepository.existsBySlotId(slot.id)
            if (hasAppointments) {
                log.warn("Skipping slot {} - has associated appointments", slot.id)
            }
            !hasAppointments
        }
        
        eligibleSlots.forEach { slot ->
            // Audit log before deletion
            auditLogService.log(
                actorId = actorId,
                actorRole = actorRole,
                actionType = "SLOT_HARD_DELETED",
                entityType = "Slot",
                entityId = slot.id,
                metadata = mapOf(
                    "branchId" to slot.branchId,
                    "serviceTypeId" to slot.serviceTypeId,
                    "deletedAt" to slot.deletedAt.toString(),
                    "retentionPeriodDays" to retentionConfig.retentionPeriodDays,
                    "hardDeletedAt" to OffsetDateTime.now().toString()
                )
            )
            
            slotRepository.delete(slot)
            log.info("Hard deleted slot: {}", slot.id)
        }
        
        log.info("Cleanup completed. Deleted {} slots", eligibleSlots.size)
        
        return CleanupSummaryResponse(
            slotsDeleted = eligibleSlots.size,
            retentionPeriodDays = retentionConfig.retentionPeriodDays,
            cutoffDate = cutoffDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        )
    }
}
