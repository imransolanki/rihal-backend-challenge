package com.flowcare.backend.auth.service

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import com.flowcare.backend.auth.model.UserPrincipal
import com.flowcare.backend.auth.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class BranchAccessServiceTest {

    private val userRepository: UserRepository = mock()
    private val service = BranchAccessService(userRepository)

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun authenticate(role: Role, branchId: String? = null) {
        val user = User(id = "u1", username = "u", password = "p", role = role,
            fullName = "U", email = "u@test.com", branchId = branchId)
        val principal = UserPrincipal(user)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
    }

    @Test fun `admin has access to any branch`() {
        authenticate(Role.ADMIN)
        assertThat(service.hasAccessToBranch("br_any")).isTrue()
    }

    @Test fun `manager has access only to assigned branch`() {
        authenticate(Role.BRANCH_MANAGER, "br_muscat_001")
        assertThat(service.hasAccessToBranch("br_muscat_001")).isTrue()
        assertThat(service.hasAccessToBranch("br_suhar_001")).isFalse()
    }

    @Test fun `staff has access only to assigned branch`() {
        authenticate(Role.STAFF, "br_suhar_001")
        assertThat(service.hasAccessToBranch("br_suhar_001")).isTrue()
        assertThat(service.hasAccessToBranch("br_muscat_001")).isFalse()
    }

    @Test fun `customer has no branch access`() {
        authenticate(Role.CUSTOMER)
        assertThat(service.hasAccessToBranch("br_muscat_001")).isFalse()
    }

    @Test fun `role checks return correct values`() {
        authenticate(Role.ADMIN)
        assertThat(service.isAdmin()).isTrue()
        assertThat(service.isBranchManager()).isFalse()

        authenticate(Role.BRANCH_MANAGER, "br_muscat_001")
        assertThat(service.isBranchManager()).isTrue()
        assertThat(service.isAdmin()).isFalse()

        authenticate(Role.STAFF, "br_muscat_001")
        assertThat(service.isStaff()).isTrue()

        authenticate(Role.CUSTOMER)
        assertThat(service.isCustomer()).isTrue()
    }

    @Test fun `getCurrentUserId and getCurrentUserBranchId return correct values`() {
        authenticate(Role.BRANCH_MANAGER, "br_muscat_001")
        assertThat(service.getCurrentUserId()).isEqualTo("u1")
        assertThat(service.getCurrentUserBranchId()).isEqualTo("br_muscat_001")
    }

    @Test fun `returns false when not authenticated`() {
        SecurityContextHolder.clearContext()
        assertThat(service.hasAccessToBranch("br_any")).isFalse()
        assertThat(service.isAdmin()).isFalse()
        assertThat(service.getCurrentUserId()).isNull()
    }
}
