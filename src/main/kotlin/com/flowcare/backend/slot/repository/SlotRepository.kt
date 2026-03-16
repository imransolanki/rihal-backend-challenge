package com.flowcare.backend.slot.repository

import com.flowcare.backend.slot.model.Slot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface SlotRepository : JpaRepository<Slot, String> {
    
    @Query("""
        SELECT s FROM Slot s 
        WHERE s.branchId = :branchId 
        AND s.serviceTypeId = :serviceTypeId 
        AND s.startAt > :now 
        AND s.deletedAt IS NULL 
        AND s.isActive = true 
        AND s.bookedCount < s.capacity
        ORDER BY s.startAt ASC
    """)
    fun findAvailableSlots(
        branchId: String,
        serviceTypeId: String,
        now: OffsetDateTime
    ): List<Slot>
    
    @Query("""
        SELECT s FROM Slot s 
        WHERE s.branchId = :branchId 
        AND s.serviceTypeId = :serviceTypeId 
        AND DATE(s.startAt) = DATE(:date)
        AND s.startAt > :now 
        AND s.deletedAt IS NULL 
        AND s.isActive = true 
        AND s.bookedCount < s.capacity
        ORDER BY s.startAt ASC
    """)
    fun findAvailableSlotsByDate(
        branchId: String,
        serviceTypeId: String,
        date: OffsetDateTime,
        now: OffsetDateTime
    ): List<Slot>

    fun findByBranchId(branchId: String): List<Slot>

    fun findByBranchIdAndServiceTypeId(branchId: String, serviceTypeId: String): List<Slot>

    @Query("""
        SELECT s FROM Slot s 
        WHERE s.branchId = :branchId 
        AND s.deletedAt IS NULL
        ORDER BY s.startAt ASC
    """)
    fun findActiveByBranchId(branchId: String): List<Slot>

    @Query("""
        SELECT s FROM Slot s 
        WHERE s.deletedAt IS NOT NULL
        AND s.deletedAt < :cutoffDate
    """)
    fun findSoftDeletedBefore(cutoffDate: OffsetDateTime): List<Slot>
}
