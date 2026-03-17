package com.flowcare.backend.config.controller

import com.flowcare.backend.config.dto.RetentionConfigResponse
import com.flowcare.backend.config.dto.UpdateRetentionRequest
import com.flowcare.backend.config.service.SystemConfigService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/config")
class SystemConfigController(
    private val configService: SystemConfigService
) {
    
    @GetMapping("/retention-period")
    @PreAuthorize("hasRole('ADMIN')")
    fun getRetentionPeriod(): RetentionConfigResponse {
        return configService.getRetentionPeriod()
    }
    
    @PutMapping("/retention-period")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateRetentionPeriod(
        @RequestBody request: UpdateRetentionRequest
    ): RetentionConfigResponse {
        return configService.updateRetentionPeriod(request)
    }
}
