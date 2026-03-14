package com.flowcare.backend.auth.service

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.UserPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class BranchAccessService {

    private fun currentUser() =
        (SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal)?.getUser()

    fun hasAccessToBranch(branchId: String): Boolean {
        val user = currentUser() ?: return false
        return when (user.role) {
            Role.ADMIN -> true
            Role.BRANCH_MANAGER, Role.STAFF -> user.branchId == branchId
            Role.CUSTOMER -> false
        }
    }

    fun isAdmin(): Boolean = currentUser()?.role == Role.ADMIN
    fun isBranchManager(): Boolean = currentUser()?.role == Role.BRANCH_MANAGER
    fun isStaff(): Boolean = currentUser()?.role == Role.STAFF
    fun isCustomer(): Boolean = currentUser()?.role == Role.CUSTOMER

    fun getCurrentUserId(): String? = currentUser()?.id
    fun getCurrentUserBranchId(): String? = currentUser()?.branchId
}
