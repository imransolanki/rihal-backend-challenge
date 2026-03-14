package com.flowcare.backend.staff.repository

import com.flowcare.backend.staff.model.StaffServiceType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StaffServiceTypeRepository : JpaRepository<StaffServiceType, Int> {
    fun existsByStaffIdAndServiceTypeId(staffId: String, serviceTypeId: String): Boolean
}
