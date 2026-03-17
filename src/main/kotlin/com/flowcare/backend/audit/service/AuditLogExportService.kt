package com.flowcare.backend.audit.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.flowcare.backend.audit.repository.AuditLogRepository
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
class AuditLogExportService(
    private val auditLogRepository: AuditLogRepository,
    private val objectMapper: ObjectMapper
) {
    
    fun exportToCsv(startDate: LocalDate?, endDate: LocalDate?): ByteArray {
        val logs = if (startDate != null && endDate != null) {
            val start = startDate.atStartOfDay().atOffset(OffsetDateTime.now().offset)
            val end = endDate.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().offset)
            auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end)
        } else {
            auditLogRepository.findAllByOrderByTimestampDesc()
        }
        
        val outputStream = ByteArrayOutputStream()
        val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
        
        // Write CSV header
        writer.write("ID,Actor ID,Actor Role,Action Type,Entity Type,Entity ID,Timestamp,Metadata\n")
        
        // Write data rows
        logs.forEach { log ->
            val metadataJson = if (log.metadata != null) {
                objectMapper.writeValueAsString(log.metadata).replace("\"", "\"\"")
            } else {
                ""
            }
            
            val row = listOf(
                log.id,
                log.actorId,
                log.actorRole,
                log.actionType,
                log.entityType,
                log.entityId,
                log.timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "\"$metadataJson\""
            ).joinToString(",")
            
            writer.write("$row\n")
        }
        
        writer.flush()
        return outputStream.toByteArray()
    }
}
