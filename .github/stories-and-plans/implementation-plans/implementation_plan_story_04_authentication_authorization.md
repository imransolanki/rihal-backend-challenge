# Story 4: Authenticate and Access the System - Implementation Plan

## Overview
Implement and validate role-based access control (RBAC) for all user types (Admin, Branch Manager, Staff, Customer). Create authorization helper service for branch-scoped access checks and test endpoints to demonstrate that authentication and authorization work correctly for each role.

## Architecture
Basic Authentication (Story 1) → Spring Security → UserDetailsService → Role-based authorization
- @PreAuthorize annotations for method-level security
- BranchAccessService for reusable branch-scoped authorization logic
- Test endpoints to validate all AC scenarios
- SpEL expressions for declarative authorization rules

## Implementation Phases

### Phase 1: Enable Method-Level Security
**Files**: 
- `src/main/kotlin/com/flowcare/backend/config/SecurityConfig.kt` (update)

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/config/MethodSecurityTest.kt`

Enable Spring Security's method-level security to support @PreAuthorize annotations.

**Key code changes:**

```kotlin
// Update src/main/kotlin/com/flowcare/backend/config/SecurityConfig.kt
package com.flowcare.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig {
    
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
    
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/health").permitAll()
                    .requestMatchers("/api/auth/register").permitAll()
                    .anyRequest().authenticated()
            }
            .httpBasic { }
            .sessionManagement { 
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) 
            }
        
        return http.build()
    }
}
```

**Test cases for this phase:**
- Test @EnableMethodSecurity is properly configured
- Test @PreAuthorize annotations are processed
- Test method security works with Basic Authentication

```kotlin
// src/test/kotlin/com/flowcare/backend/config/MethodSecurityTest.kt
package com.flowcare.backend.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.security.access.prepost.PreAuthorize
import org.assertj.core.api.Assertions.assertThat

@SpringBootTest
class MethodSecurityTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `should have method security enabled`() {
        val securityConfig = applicationContext.getBean(SecurityConfig::class.java)
        assertThat(securityConfig).isNotNull
        
        // Verify @EnableMethodSecurity is present
        val annotation = SecurityConfig::class.java.getAnnotation(
            org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity::class.java
        )
        assertThat(annotation).isNotNull
        assertThat(annotation.prePostEnabled).isTrue()
    }
}
```

**Technical details:**
- @EnableMethodSecurity replaces deprecated @EnableGlobalMethodSecurity
- prePostEnabled = true enables @PreAuthorize and @PostAuthorize
- Works with existing Basic Authentication from Story 1

---

### Phase 2: Branch Access Authorization Service
**Files**: 
- `src/main/kotlin/com/flowcare/backend/auth/service/BranchAccessService.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/auth/service/BranchAccessServiceTest.kt`

Create a service to centralize branch-scoped authorization logic for Branch Managers and Staff.

**Key code changes:**

```kotlin
// src/main/kotlin/com/flowcare/backend/auth/service/BranchAccessService.kt
package com.flowcare.backend.auth.service

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.UserPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class BranchAccessService {
    
    fun hasAccessToBranch(branchId: String): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication
        
        if (authentication == null || !authentication.isAuthenticated) {
            return false
        }
        
        val userPrincipal = authentication.principal as? UserPrincipal ?: return false
        val user = userPrincipal.getUser()
        
        return when (user.role) {
            Role.ADMIN -> true  // Admin has access to all branches
            Role.BRANCH_MANAGER, Role.STAFF -> user.branchId == branchId  // Only their assigned branch
            Role.CUSTOMER -> false  // Customers don't have branch-level access
        }
    }
    
    fun isAdmin(): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication
        
        if (authentication == null || !authentication.isAuthenticated) {
            return false
        }
        
        val userPrincipal = authentication.principal as? UserPrincipal ?: return false
        return userPrincipal.getUser().role == Role.ADMIN
    }
    
    fun isBranchManager(): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication
        
        if (authentication == null || !authentication.isAuthenticated) {
            return false
        }
        
        val userPrincipal = authentication.principal as? UserPrincipal ?: return false
        return userPrincipal.getUser().role == Role.BRANCH_MANAGER
    }
    
    fun isStaff(): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication
        
        if (authentication == null || !authentication.isAuthenticated) {
            return false
        }
        
        val userPrincipal = authentication.principal as? UserPrincipal ?: return false
        return userPrincipal.getUser().role == Role.STAFF
    }
    
    fun isCustomer(): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication
        
        if (authentication == null || !authentication.isAuthenticated) {
            return false
        }
        
        val userPrincipal = authentication.principal as? UserPrincipal ?: return false
        return userPrincipal.getUser().role == Role.CUSTOMER
    }
    
    fun getCurrentUserId(): String? {
        val authentication = SecurityContextHolder.getContext().authentication
        
        if (authentication == null || !authentication.isAuthenticated) {
            return null
        }
        
        val userPrincipal = authentication.principal as? UserPrincipal ?: return null
        return userPrincipal.getUser().id
    }
    
    fun getCurrentUserBranchId(): String? {
        val authentication = SecurityContextHolder.getContext().authentication
        
        if (authentication == null || !authentication.isAuthenticated) {
            return null
        }
        
        val userPrincipal = authentication.principal as? UserPrincipal ?: return null
        return userPrincipal.getUser().branchId
    }
}
```

**Test cases for this phase:**
- Test Admin has access to all branches
- Test Branch Manager has access only to assigned branch
- Test Staff has access only to assigned branch
- Test Customer has no branch-level access
- Test role checking methods work correctly
- Test getCurrentUserId returns correct user ID
- Test getCurrentUserBranchId returns correct branch ID

```kotlin
// src/test/kotlin/com/flowcare/backend/auth/service/BranchAccessServiceTest.kt
package com.flowcare.backend.auth.service

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import com.flowcare.backend.auth.model.UserPrincipal
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.assertj.core.api.Assertions.assertThat

class BranchAccessServiceTest {

    private val branchAccessService = BranchAccessService()

    @Test
    fun `admin should have access to all branches`() {
        val admin = User(
            id = "admin_001",
            username = "admin",
            password = "password",
            role = Role.ADMIN,
            fullName = "Admin User",
            email = "admin@test.com"
        )
        
        setAuthentication(admin)
        
        assertThat(branchAccessService.hasAccessToBranch("br_muscat_001")).isTrue()
        assertThat(branchAccessService.hasAccessToBranch("br_suhar_001")).isTrue()
        assertThat(branchAccessService.hasAccessToBranch("any_branch")).isTrue()
    }

    @Test
    fun `branch manager should have access only to assigned branch`() {
        val manager = User(
            id = "mgr_001",
            username = "manager",
            password = "password",
            role = Role.BRANCH_MANAGER,
            fullName = "Manager User",
            email = "manager@test.com",
            branchId = "br_muscat_001"
        )
        
        setAuthentication(manager)
        
        assertThat(branchAccessService.hasAccessToBranch("br_muscat_001")).isTrue()
        assertThat(branchAccessService.hasAccessToBranch("br_suhar_001")).isFalse()
    }

    @Test
    fun `staff should have access only to assigned branch`() {
        val staff = User(
            id = "staff_001",
            username = "staff",
            password = "password",
            role = Role.STAFF,
            fullName = "Staff User",
            email = "staff@test.com",
            branchId = "br_suhar_001"
        )
        
        setAuthentication(staff)
        
        assertThat(branchAccessService.hasAccessToBranch("br_suhar_001")).isTrue()
        assertThat(branchAccessService.hasAccessToBranch("br_muscat_001")).isFalse()
    }

    @Test
    fun `customer should not have branch-level access`() {
        val customer = User(
            id = "cust_001",
            username = "customer",
            password = "password",
            role = Role.CUSTOMER,
            fullName = "Customer User",
            email = "customer@test.com"
        )
        
        setAuthentication(customer)
        
        assertThat(branchAccessService.hasAccessToBranch("br_muscat_001")).isFalse()
        assertThat(branchAccessService.hasAccessToBranch("br_suhar_001")).isFalse()
    }

    @Test
    fun `should correctly identify user roles`() {
        val admin = User(
            id = "admin_001", username = "admin", password = "password",
            role = Role.ADMIN, fullName = "Admin", email = "admin@test.com"
        )
        setAuthentication(admin)
        assertThat(branchAccessService.isAdmin()).isTrue()
        assertThat(branchAccessService.isBranchManager()).isFalse()
        
        val manager = User(
            id = "mgr_001", username = "manager", password = "password",
            role = Role.BRANCH_MANAGER, fullName = "Manager", email = "mgr@test.com"
        )
        setAuthentication(manager)
        assertThat(branchAccessService.isAdmin()).isFalse()
        assertThat(branchAccessService.isBranchManager()).isTrue()
    }

    @Test
    fun `should return current user ID and branch ID`() {
        val manager = User(
            id = "mgr_001",
            username = "manager",
            password = "password",
            role = Role.BRANCH_MANAGER,
            fullName = "Manager User",
            email = "manager@test.com",
            branchId = "br_muscat_001"
        )
        
        setAuthentication(manager)
        
        assertThat(branchAccessService.getCurrentUserId()).isEqualTo("mgr_001")
        assertThat(branchAccessService.getCurrentUserBranchId()).isEqualTo("br_muscat_001")
    }

    private fun setAuthentication(user: User) {
        val userPrincipal = UserPrincipal(user)
        val authentication = UsernamePasswordAuthenticationToken(
            userPrincipal,
            user.password,
            userPrincipal.authorities
        )
        SecurityContextHolder.getContext().authentication = authentication
    }
}
```

**Technical details:**
- Accesses SecurityContextHolder to get current authenticated user
- Reusable across all features requiring branch-scoped authorization
- Returns boolean for easy use in @PreAuthorize SpEL expressions
- Helper methods for role checking and getting current user info


---

### Phase 3: Role-Based Test Endpoints
**Files**: 
- `src/main/kotlin/com/flowcare/backend/common/controller/AuthTestController.kt`
- `src/main/kotlin/com/flowcare/backend/common/dto/AuthTestResponse.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/common/controller/AuthTestControllerTest.kt`

Create test endpoints to validate all role-based access control scenarios from the acceptance criteria.

**Key code changes:**

```kotlin
// src/main/kotlin/com/flowcare/backend/common/dto/AuthTestResponse.kt
package com.flowcare.backend.common.dto

data class AuthTestResponse(
    val message: String,
    val userId: String,
    val username: String,
    val role: String,
    val branchId: String? = null,
    val accessGranted: Boolean = true
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/common/controller/AuthTestController.kt
package com.flowcare.backend.common.controller

import com.flowcare.backend.auth.model.UserPrincipal
import com.flowcare.backend.auth.service.BranchAccessService
import com.flowcare.backend.common.dto.AuthTestResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/test")
class AuthTestController(
    private val branchAccessService: BranchAccessService
) {
    
    // AC 3: Admin can access system-wide features
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun adminEndpoint(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<AuthTestResponse> {
        val user = userPrincipal.getUser()
        return ResponseEntity.ok(
            AuthTestResponse(
                message = "Admin access granted - system-wide features available",
                userId = user.id,
                username = user.username,
                role = user.role.name,
                branchId = user.branchId
            )
        )
    }
    
    // AC 4 & 5: Branch Manager can access only their branch
    @GetMapping("/manager/{branchId}")
    @PreAuthorize("hasRole('BRANCH_MANAGER') and @branchAccessService.hasAccessToBranch(#branchId)")
    fun managerBranchEndpoint(
        @PathVariable branchId: String,
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<AuthTestResponse> {
        val user = userPrincipal.getUser()
        return ResponseEntity.ok(
            AuthTestResponse(
                message = "Branch Manager access granted to branch: $branchId",
                userId = user.id,
                username = user.username,
                role = user.role.name,
                branchId = user.branchId
            )
        )
    }
    
    // AC 6: Staff can access their assigned appointments
    @GetMapping("/staff")
    @PreAuthorize("hasRole('STAFF')")
    fun staffEndpoint(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<AuthTestResponse> {
        val user = userPrincipal.getUser()
        return ResponseEntity.ok(
            AuthTestResponse(
                message = "Staff access granted - can view assigned appointments",
                userId = user.id,
                username = user.username,
                role = user.role.name,
                branchId = user.branchId
            )
        )
    }
    
    // AC 7: Staff cannot create/delete slots (admin/manager only)
    @PostMapping("/slots")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BRANCH_MANAGER')")
    fun createSlotEndpoint(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<AuthTestResponse> {
        val user = userPrincipal.getUser()
        return ResponseEntity.ok(
            AuthTestResponse(
                message = "Slot creation access granted",
                userId = user.id,
                username = user.username,
                role = user.role.name,
                branchId = user.branchId
            )
        )
    }
    
    // AC 8: Customer can access booking features
    @GetMapping("/customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    fun customerEndpoint(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<AuthTestResponse> {
        val user = userPrincipal.getUser()
        return ResponseEntity.ok(
            AuthTestResponse(
                message = "Customer access granted - can manage own appointments",
                userId = user.id,
                username = user.username,
                role = user.role.name
            )
        )
    }
    
    // AC 9: Customer cannot access administrative features
    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    fun adminOnlyEndpoint(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<AuthTestResponse> {
        val user = userPrincipal.getUser()
        return ResponseEntity.ok(
            AuthTestResponse(
                message = "Administrative feature access granted",
                userId = user.id,
                username = user.username,
                role = user.role.name
            )
        )
    }
}
```

**Test cases for this phase:**
- Test Admin can access /api/test/admin endpoint
- Test Branch Manager can access their branch endpoint
- Test Branch Manager cannot access other branch endpoint (403)
- Test Staff can access /api/test/staff endpoint
- Test Staff cannot access /api/test/slots endpoint (403)
- Test Customer can access /api/test/customer endpoint
- Test Customer cannot access /api/test/admin-only endpoint (403)
- Test unauthenticated request returns 401
- Test all roles return correct user information

```kotlin
// src/test/kotlin/com/flowcare/backend/common/controller/AuthTestControllerTest.kt
package com.flowcare.backend.common.controller

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import com.flowcare.backend.auth.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class AuthTestControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun setup() {
        userRepository.deleteAll()
        
        // Create test users
        val admin = User(
            id = "admin_test",
            username = "admin_test",
            password = passwordEncoder.encode("password"),
            role = Role.ADMIN,
            fullName = "Admin Test",
            email = "admin_test@test.com"
        )
        
        val manager = User(
            id = "mgr_test",
            username = "mgr_test",
            password = passwordEncoder.encode("password"),
            role = Role.BRANCH_MANAGER,
            fullName = "Manager Test",
            email = "mgr_test@test.com",
            branchId = "br_muscat_001"
        )
        
        val staff = User(
            id = "staff_test",
            username = "staff_test",
            password = passwordEncoder.encode("password"),
            role = Role.STAFF,
            fullName = "Staff Test",
            email = "staff_test@test.com",
            branchId = "br_muscat_001"
        )
        
        val customer = User(
            id = "cust_test",
            username = "cust_test",
            password = passwordEncoder.encode("password"),
            role = Role.CUSTOMER,
            fullName = "Customer Test",
            email = "cust_test@test.com"
        )
        
        userRepository.saveAll(listOf(admin, manager, staff, customer))
    }

    @Test
    fun `admin should access admin endpoint`() {
        mockMvc.perform(
            get("/api/test/admin")
                .with(httpBasic("admin_test", "password"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Admin access granted - system-wide features available"))
            .andExpect(jsonPath("$.role").value("ADMIN"))
    }

    @Test
    fun `branch manager should access their branch endpoint`() {
        mockMvc.perform(
            get("/api/test/manager/br_muscat_001")
                .with(httpBasic("mgr_test", "password"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Branch Manager access granted to branch: br_muscat_001"))
            .andExpect(jsonPath("$.branchId").value("br_muscat_001"))
    }

    @Test
    fun `branch manager should not access other branch endpoint`() {
        mockMvc.perform(
            get("/api/test/manager/br_suhar_001")
                .with(httpBasic("mgr_test", "password"))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `staff should access staff endpoint`() {
        mockMvc.perform(
            get("/api/test/staff")
                .with(httpBasic("staff_test", "password"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Staff access granted - can view assigned appointments"))
            .andExpect(jsonPath("$.role").value("STAFF"))
    }

    @Test
    fun `staff should not access slot creation endpoint`() {
        mockMvc.perform(
            post("/api/test/slots")
                .with(httpBasic("staff_test", "password"))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `admin should access slot creation endpoint`() {
        mockMvc.perform(
            post("/api/test/slots")
                .with(httpBasic("admin_test", "password"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Slot creation access granted"))
    }

    @Test
    fun `customer should access customer endpoint`() {
        mockMvc.perform(
            get("/api/test/customer")
                .with(httpBasic("cust_test", "password"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Customer access granted - can manage own appointments"))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
    }

    @Test
    fun `customer should not access admin-only endpoint`() {
        mockMvc.perform(
            get("/api/test/admin-only")
                .with(httpBasic("cust_test", "password"))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `unauthenticated request should return 401`() {
        mockMvc.perform(get("/api/test/admin"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `invalid credentials should return 401`() {
        mockMvc.perform(
            get("/api/test/admin")
                .with(httpBasic("admin_test", "wrongpassword"))
        )
            .andExpect(status().isUnauthorized)
    }
}
```

**Technical details:**
- @PreAuthorize with SpEL expressions for declarative authorization
- hasRole() checks for specific roles
- @branchAccessService.hasAccessToBranch() for branch-scoped checks
- Returns 403 Forbidden when authorization fails
- Returns 401 Unauthorized when authentication fails

**Note on Test Endpoints:**
These endpoints serve to validate that role-based authorization works correctly. They can be:
- Kept permanently as authorization health checks for monitoring
- Removed once real feature endpoints (Stories 5-20) are implemented and tested
- Disabled in production via Spring profiles (@Profile("dev", "test"))
- Used for integration testing and debugging authorization issues


---

### Phase 4: Documentation and Examples
**Files**: 
- `README_SETUP.md` (update)

**Test Files**: None (documentation phase)

Update documentation with authentication and authorization examples for all user roles.

**Key code changes:**

Add to README_SETUP.md:

```markdown
## Authentication and Authorization

FlowCare uses HTTP Basic Authentication with role-based access control (RBAC).

### User Roles

| Role | Access Level | Scope |
|------|-------------|-------|
| ADMIN | System-wide | All branches, all data |
| BRANCH_MANAGER | Branch-scoped | Only assigned branch |
| STAFF | Branch-scoped | Only assigned appointments |
| CUSTOMER | Self-scoped | Only own appointments |

### Authentication

All protected endpoints require Basic Authentication:

```bash
curl -u username:password http://localhost:8080/api/endpoint
```

### Test Credentials (from seed data)

| Username | Password | Role | Branch |
|----------|----------|------|--------|
| admin | Admin@123 | ADMIN | - |
| mgr_muscat | Manager@123 | BRANCH_MANAGER | Muscat |
| mgr_suhar | Manager@123 | BRANCH_MANAGER | Suhar |
| staff_muscat_1 | Staff@123 | STAFF | Muscat |
| staff_muscat_2 | Staff@123 | STAFF | Muscat |
| staff_suhar_1 | Staff@123 | STAFF | Suhar |
| staff_suhar_2 | Staff@123 | STAFF | Suhar |
| cust_ahmed | Customer@123 | CUSTOMER | - |
| cust_fatima | Customer@123 | CUSTOMER | - |
| cust_khalid | Customer@123 | CUSTOMER | - |

### Authorization Examples

#### Admin Access (System-wide)

```bash
# Admin can access admin endpoint
curl -u admin:Admin@123 http://localhost:8080/api/test/admin
```

Response:
```json
{
  "message": "Admin access granted - system-wide features available",
  "userId": "usr_admin_001",
  "username": "admin",
  "role": "ADMIN",
  "branchId": null,
  "accessGranted": true
}
```

#### Branch Manager Access (Branch-scoped)

```bash
# Manager can access their assigned branch
curl -u mgr_muscat:Manager@123 http://localhost:8080/api/test/manager/br_muscat_001
```

Response:
```json
{
  "message": "Branch Manager access granted to branch: br_muscat_001",
  "userId": "usr_mgr_001",
  "username": "mgr_muscat",
  "role": "BRANCH_MANAGER",
  "branchId": "br_muscat_001",
  "accessGranted": true
}
```

```bash
# Manager CANNOT access other branches (403 Forbidden)
curl -u mgr_muscat:Manager@123 http://localhost:8080/api/test/manager/br_suhar_001
```

Response:
```json
{
  "error": "Forbidden",
  "message": "Access denied",
  "timestamp": "2026-03-13T16:20:00"
}
```

#### Staff Access

```bash
# Staff can access staff endpoint
curl -u staff_muscat_1:Staff@123 http://localhost:8080/api/test/staff
```

Response:
```json
{
  "message": "Staff access granted - can view assigned appointments",
  "userId": "usr_staff_001",
  "username": "staff_muscat_1",
  "role": "STAFF",
  "branchId": "br_muscat_001",
  "accessGranted": true
}
```

```bash
# Staff CANNOT create slots (403 Forbidden)
curl -X POST -u staff_muscat_1:Staff@123 http://localhost:8080/api/test/slots
```

Response:
```json
{
  "error": "Forbidden",
  "message": "Access denied",
  "timestamp": "2026-03-13T16:20:00"
}
```

#### Customer Access

```bash
# Customer can access customer endpoint
curl -u cust_ahmed:Customer@123 http://localhost:8080/api/test/customer
```

Response:
```json
{
  "message": "Customer access granted - can manage own appointments",
  "userId": "usr_cust_001",
  "username": "cust_ahmed",
  "role": "CUSTOMER",
  "branchId": null,
  "accessGranted": true
}
```

```bash
# Customer CANNOT access admin features (403 Forbidden)
curl -u cust_ahmed:Customer@123 http://localhost:8080/api/test/admin-only
```

Response:
```json
{
  "error": "Forbidden",
  "message": "Access denied",
  "timestamp": "2026-03-13T16:20:00"
}
```

#### Unauthenticated Access

```bash
# No credentials provided (401 Unauthorized)
curl http://localhost:8080/api/test/admin
```

Response:
```json
{
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "timestamp": "2026-03-13T16:20:00"
}
```

```bash
# Invalid credentials (401 Unauthorized)
curl -u admin:wrongpassword http://localhost:8080/api/test/admin
```

Response:
```json
{
  "error": "Unauthorized",
  "message": "Authentication failed",
  "timestamp": "2026-03-13T16:20:00"
}
```

### Public Endpoints (No Authentication Required)

The following endpoints are publicly accessible:

- `GET /api/health` - Health check
- `POST /api/auth/register` - Customer registration
- `GET /api/branches` - List branches (Story 5)
- `GET /api/branches/{branchId}/services` - List services (Story 5)
- `GET /api/slots` - List available slots (Story 6)

```bash
# Public endpoint - no authentication needed
curl http://localhost:8080/api/health
```

### Authorization Rules Summary

| Endpoint Pattern | Admin | Branch Manager | Staff | Customer |
|-----------------|-------|----------------|-------|----------|
| /api/test/admin | ✓ | ✗ | ✗ | ✗ |
| /api/test/manager/{branchId} | ✓ | ✓ (own branch) | ✗ | ✗ |
| /api/test/staff | ✓ | ✓ | ✓ | ✗ |
| /api/test/slots (POST) | ✓ | ✓ | ✗ | ✗ |
| /api/test/customer | ✓ | ✓ | ✓ | ✓ |
| /api/test/admin-only | ✓ | ✗ | ✗ | ✗ |

### HTTP Status Codes

| Status | Meaning | When |
|--------|---------|------|
| 200 OK | Success | Request authorized and successful |
| 401 Unauthorized | Authentication failed | No credentials or invalid credentials |
| 403 Forbidden | Authorization failed | Valid credentials but insufficient permissions |

### Troubleshooting

**Issue: Always getting 401 Unauthorized**
- Verify credentials are correct
- Check that user exists in database (seeded or registered)
- Ensure password matches (case-sensitive)

**Issue: Getting 403 Forbidden**
- Check user role matches endpoint requirements
- For branch-scoped endpoints, verify user's branchId matches requested branch
- Review authorization rules in the table above

**Issue: "Access denied" for Branch Manager**
- Verify the manager's branchId in the database matches the requested branch
- Ensure the endpoint path includes the correct branchId parameter
```

**Technical details:**
- Clear examples for all user roles
- Shows both success and failure scenarios
- Documents HTTP status codes
- Provides troubleshooting guidance
- Includes authorization rules matrix

---

## Technical Considerations

**Dependencies:**
- Story 1 (Basic Authentication, UserDetailsService, SecurityConfig)
- Story 2 (Seeded users with roles and branch assignments)
- Spring Security method-level security

**Edge Cases:**
- User without branchId trying to access branch-scoped endpoint (denied)
- Admin accessing branch-scoped endpoint (allowed for all branches)
- Concurrent authentication requests (stateless, no session issues)
- User role changed while authenticated (requires re-authentication)

**Testing Strategy:**
- Unit tests for BranchAccessService logic
- Integration tests for all authorization scenarios
- Test all role combinations against all endpoints
- Test both success and failure cases
- Verify correct HTTP status codes (401 vs 403)

**Performance:**
- Stateless authentication (no session overhead)
- SecurityContextHolder is thread-local (safe for concurrent requests)
- Authorization checks are fast (in-memory role checks)

**Security:**
- Method-level security prevents bypassing authorization
- Branch-scoped access prevents cross-branch data leaks
- Clear separation of concerns (authentication vs authorization)
- Fail-safe defaults (deny access unless explicitly allowed)

## Success Criteria

- [ ] @EnableMethodSecurity is configured with prePostEnabled = true
- [ ] BranchAccessService correctly checks branch access for all roles
- [ ] Admin has access to all branches
- [ ] Branch Manager has access only to assigned branch
- [ ] Branch Manager denied access to other branches (403)
- [ ] Staff has access to staff endpoint
- [ ] Staff denied access to slot creation endpoint (403)
- [ ] Customer has access to customer endpoint
- [ ] Customer denied access to admin endpoint (403)
- [ ] Unauthenticated requests return 401
- [ ] Invalid credentials return 401
- [ ] Insufficient permissions return 403
- [ ] All test endpoints return correct user information
- [ ] @PreAuthorize annotations work correctly with SpEL expressions
- [ ] BranchAccessService methods return correct boolean values
- [ ] getCurrentUserId and getCurrentUserBranchId return correct values
- [ ] README documents all authentication and authorization scenarios
- [ ] All tests pass successfully
