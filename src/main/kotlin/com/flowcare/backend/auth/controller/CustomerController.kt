package com.flowcare.backend.auth.controller

import com.flowcare.backend.audit.service.AuditLogService
import com.flowcare.backend.auth.dto.CustomerDetailResponse
import com.flowcare.backend.auth.dto.CustomerListResponse
import com.flowcare.backend.auth.service.BranchAccessService
import com.flowcare.backend.auth.service.CustomerService
import com.flowcare.backend.storage.service.FileStorageService
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.nio.file.Files

@RestController
@RequestMapping("/api/customers")
class CustomerController(
    private val customerService: CustomerService,
    private val branchAccessService: BranchAccessService,
    private val fileStorageService: FileStorageService,
    private val auditLogService: AuditLogService
) {
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun listCustomers(
        @AuthenticationPrincipal userDetails: UserDetails,
        pageable: Pageable
    ): Page<CustomerListResponse> {
        val user = branchAccessService.getCurrentUser(userDetails.username)
        
        return if (branchAccessService.isAdmin(user)) {
            customerService.listCustomers(pageable)
        } else {
            val branchId = branchAccessService.getUserBranchId(user)
            customerService.listCustomersByBranch(branchId, pageable)
        }
    }
    
    @GetMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    fun getCustomerDetail(
        @PathVariable customerId: String
    ): CustomerDetailResponse {
        return customerService.getCustomerDetail(customerId)
    }
    
    @GetMapping("/{customerId}/id-document")
    @PreAuthorize("hasRole('ADMIN')")
    fun downloadIdDocument(
        @PathVariable customerId: String,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<Resource> {
        val user = branchAccessService.getCurrentUser(userDetails.username)
        val customerDetail = customerService.getCustomerDetail(customerId)
        
        if (customerDetail.idDocumentPath == null) {
            return ResponseEntity.notFound().build()
        }
        
        val filePath = fileStorageService.loadFile(customerDetail.idDocumentPath)
        
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build()
        }
        
        // Audit log ID document access
        auditLogService.log(
            actorId = user.id,
            actorRole = user.role.name,
            actionType = "ID_DOCUMENT_ACCESSED",
            entityType = "Customer",
            entityId = customerId,
            metadata = mapOf(
                "documentPath" to customerDetail.idDocumentPath,
                "customerName" to customerDetail.fullName
            )
        )
        
        val resource = UrlResource(filePath.toUri())
        val contentType = Files.probeContentType(filePath) ?: "application/octet-stream"
        
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${filePath.fileName}\"")
            .body(resource)
    }
}
