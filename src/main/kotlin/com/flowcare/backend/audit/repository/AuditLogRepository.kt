package com.flowcare.backend.audit.repository

import com.flowcare.backend.audit.model.AuditLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, String>
