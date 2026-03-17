package com.flowcare.backend.config.service

import com.flowcare.backend.config.dto.RetentionConfigResponse
import com.flowcare.backend.config.dto.UpdateRetentionRequest
import com.flowcare.backend.config.model.SystemConfig
import com.flowcare.backend.config.repository.SystemConfigRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class SystemConfigService(
    private val configRepository: SystemConfigRepository
) {
    companion object {
        const val RETENTION_PERIOD_KEY = "RETENTION_PERIOD_DAYS"
        const val DEFAULT_RETENTION_DAYS = 30
    }
    
    fun getRetentionPeriod(): RetentionConfigResponse {
        val config = configRepository.findById(RETENTION_PERIOD_KEY)
            .orElse(createDefaultRetentionConfig())
        
        return RetentionConfigResponse(
            retentionPeriodDays = config.value.toInt(),
            description = config.description ?: "Soft-delete retention period"
        )
    }
    
    fun updateRetentionPeriod(request: UpdateRetentionRequest): RetentionConfigResponse {
        require(request.retentionPeriodDays > 0) { 
            "Retention period must be positive" 
        }
        
        val config = configRepository.findById(RETENTION_PERIOD_KEY)
            .orElse(createDefaultRetentionConfig())
        
        config.value = request.retentionPeriodDays.toString()
        config.updatedAt = LocalDateTime.now()
        
        configRepository.save(config)
        
        return RetentionConfigResponse(
            retentionPeriodDays = config.value.toInt(),
            description = config.description ?: "Soft-delete retention period"
        )
    }
    
    private fun createDefaultRetentionConfig(): SystemConfig {
        val config = SystemConfig(
            key = RETENTION_PERIOD_KEY,
            value = DEFAULT_RETENTION_DAYS.toString(),
            description = "Number of days to retain soft-deleted slots before hard deletion"
        )
        return configRepository.save(config)
    }
}
