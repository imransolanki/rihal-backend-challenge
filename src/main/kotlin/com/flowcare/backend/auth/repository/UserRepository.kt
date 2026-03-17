package com.flowcare.backend.auth.repository

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, String> {
    fun findByUsername(username: String): Optional<User>
    fun findByEmail(email: String): Optional<User>
    fun findByRole(role: Role, pageable: Pageable): Page<User>
    fun findByRoleAndBranchId(role: Role, branchId: String, pageable: Pageable): Page<User>
    
    @Query("""
        SELECT DISTINCT u FROM User u 
        JOIN Appointment a ON a.customerId = u.id 
        JOIN Slot s ON a.slotId = s.id 
        WHERE u.role = 'CUSTOMER' AND s.branchId = :branchId
    """)
    fun findCustomersWithAppointmentsAtBranch(branchId: String, pageable: Pageable): Page<User>
}
