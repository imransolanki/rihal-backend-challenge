package com.flowcare.backend.audit.repository

import com.flowcare.backend.audit.model.AuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, String> {
    fun findByTimestampBetween(start: OffsetDateTime, end: OffsetDateTime, pageable: Pageable): Page<AuditLog>
    
    @Query("""
        SELECT a FROM AuditLog a 
        WHERE FUNCTION('jsonb_extract_path_text', a.metadata, 'branchId') = :branchId
    """)
    fun findByBranchId(branchId: String, pageable: Pageable): Page<AuditLog>
    
    @Query("""
        SELECT a FROM AuditLog a 
        WHERE FUNCTION('jsonb_extract_path_text', a.metadata, 'branchId') = :branchId
        AND a.timestamp BETWEEN :start AND :end
    """)
    fun findByBranchIdAndTimestampBetween(
        branchId: String, 
        start: OffsetDateTime, 
        end: OffsetDateTime, 
        pageable: Pageable
    ): Page<AuditLog>
    
    fun findAllByOrderByTimestampDesc(): List<AuditLog>
    
    fun findByTimestampBetweenOrderByTimestampDesc(start: OffsetDateTime, end: OffsetDateTime): List<AuditLog>
}
