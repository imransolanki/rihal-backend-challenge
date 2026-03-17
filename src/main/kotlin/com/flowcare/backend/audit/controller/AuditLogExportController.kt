package com.flowcare.backend.audit.controller

import com.flowcare.backend.audit.service.AuditLogExportService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/admin/audit-logs")
class AuditLogExportController(
    private val exportService: AuditLogExportService
) {
    
    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    fun exportAuditLogs(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?
    ): ResponseEntity<ByteArray> {
        val csvData = exportService.exportToCsv(startDate, endDate)
        
        val filename = if (startDate != null && endDate != null) {
            "audit_logs_${startDate.format(DateTimeFormatter.ISO_DATE)}_to_${endDate.format(DateTimeFormatter.ISO_DATE)}.csv"
        } else {
            "audit_logs_all.csv"
        }
        
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .body(csvData)
    }
}
