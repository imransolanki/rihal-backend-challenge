package com.flowcare.backend.service.repository

import com.flowcare.backend.service.model.ServiceType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ServiceTypeRepository : JpaRepository<ServiceType, String>
