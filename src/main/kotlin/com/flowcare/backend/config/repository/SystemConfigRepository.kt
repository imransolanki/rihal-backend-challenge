package com.flowcare.backend.config.repository

import com.flowcare.backend.config.model.SystemConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SystemConfigRepository : JpaRepository<SystemConfig, String>
