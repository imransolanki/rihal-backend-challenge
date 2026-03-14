# Story 1: Project Setup with Database and API Framework - Implementation Plan

## Overview
Set up a minimal Spring Boot project with Kotlin, PostgreSQL database, Flyway migrations, Spring Security Basic Authentication, and a health check endpoint to establish the foundation for the FlowCare Queue & Appointment Booking System.

## Architecture
Spring Boot (Kotlin) → Spring Data JPA → PostgreSQL
Spring Security (Basic Auth) → Role-based access control
Flyway → Database migrations
Feature-based package structure (appointment/, branch/, auth/, etc.)

## Implementation Phases

### Phase 1: Initialize Spring Boot Project with Gradle
**Files**: 
- `build.gradle.kts`
- `settings.gradle.kts`
- `src/main/kotlin/com/flowcare/backend/BackendApplication.kt`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `.gitignore`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/BackendApplicationTests.kt`

Initialize a Spring Boot project with Kotlin, Gradle (Kotlin DSL), and configure essential dependencies including Spring Web, Spring Data JPA, PostgreSQL driver, Flyway, and Spring Security.

**Key code changes:**

```kotlin
// settings.gradle.kts
rootProject.name = "flowcare-backend"
```

```kotlin
// build.gradle.kts
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    kotlin("plugin.jpa") version "1.9.22"
}

group = "com.flowcare"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

```gitignore
# .gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# IDE
.idea/
*.iml
*.iws
*.ipr
out/

# OS
.DS_Store
Thumbs.db

# Application
*.log
.env

# Kiro
.kiro/
```

```kotlin
// src/main/kotlin/com/flowcare/backend/BackendApplication.kt
package com.flowcare.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BackendApplication

fun main(args: Array<String>) {
    runApplication<BackendApplication>(*args)
}
```

```yaml
# src/main/resources/application.yml
spring:
  application:
    name: flowcare-backend
  profiles:
    active: dev
  jpa:
    open-in-view: false
    show-sql: false
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    baseline-on-migrate: true
```

```yaml
# src/main/resources/application-dev.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/flowcare_db
    username: flowcare_user
    password: flowcare_pass
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
```

**Test cases for this phase:**
- Test application context loads successfully
- Test Spring Boot application starts without errors

```kotlin
// src/test/kotlin/com/flowcare/backend/BackendApplicationTests.kt
package com.flowcare.backend

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BackendApplicationTests {

    @Test
    fun contextLoads() {
        // Verifies that the application context loads successfully
    }
}
```

**Technical details:**
- Use Spring Boot 3.2.3 with Kotlin 1.9.22
- Java 17 as the target JVM version
- Gradle Kotlin DSL for type-safe build configuration
- Profile-based configuration (dev, test, prod)

---

### Phase 2: Database Connection and Initial Schema
**Files**: 
- `src/main/resources/db/migration/V1__create_users_table.sql`
- `src/main/kotlin/com/flowcare/backend/auth/model/User.kt`
- `src/main/kotlin/com/flowcare/backend/auth/model/Role.kt`
- `src/main/kotlin/com/flowcare/backend/auth/repository/UserRepository.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/auth/repository/UserRepositoryTest.kt`

Create Flyway migration scripts to set up the initial database schema with a users table and roles enum. Create corresponding JPA entities and repository.

**Key code changes:**

```sql
-- src/main/resources/db/migration/V1__create_users_table.sql
CREATE TYPE user_role AS ENUM ('ADMIN', 'BRANCH_MANAGER', 'STAFF', 'CUSTOMER');

CREATE TABLE users (
    id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role user_role NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20),
    branch_id VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_branch_id ON users(branch_id);
```

```kotlin
// src/main/kotlin/com/flowcare/backend/auth/model/Role.kt
package com.flowcare.backend.auth.model

enum class Role {
    ADMIN,
    BRANCH_MANAGER,
    STAFF,
    CUSTOMER
}
```

```kotlin
// src/main/kotlin/com/flowcare/backend/auth/model/User.kt
package com.flowcare.backend.auth.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "users")
data class User(
    @Id
    val id: String,
    
    @Column(unique = true, nullable = false)
    val username: String,
    
    @Column(nullable = false)
    val password: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role,
    
    @Column(name = "full_name", nullable = false)
    val fullName: String,
    
    @Column(unique = true, nullable = false)
    val email: String,
    
    val phone: String? = null,
    
    @Column(name = "branch_id")
    val branchId: String? = null,
    
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/auth/repository/UserRepository.kt
package com.flowcare.backend.auth.repository

import com.flowcare.backend.auth.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, String> {
    fun findByUsername(username: String): Optional<User>
    fun findByEmail(email: String): Optional<User>
}
```

**Test cases for this phase:**
- Test Flyway migration executes successfully
- Test User entity can be persisted to database
- Test UserRepository findByUsername returns correct user
- Test UserRepository findByEmail returns correct user

```kotlin
// src/test/kotlin/com/flowcare/backend/auth/repository/UserRepositoryTest.kt
package com.flowcare.backend.auth.repository

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.assertj.core.api.Assertions.assertThat

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `should save and find user by username`() {
        val user = User(
            id = "test_001",
            username = "testuser",
            password = "password",
            role = Role.CUSTOMER,
            fullName = "Test User",
            email = "test@example.com"
        )
        
        userRepository.save(user)
        
        val found = userRepository.findByUsername("testuser")
        assertThat(found).isPresent
        assertThat(found.get().fullName).isEqualTo("Test User")
    }

    @Test
    fun `should find user by email`() {
        val user = User(
            id = "test_002",
            username = "testuser2",
            password = "password",
            role = Role.STAFF,
            fullName = "Test Staff",
            email = "staff@example.com"
        )
        
        userRepository.save(user)
        
        val found = userRepository.findByEmail("staff@example.com")
        assertThat(found).isPresent
        assertThat(found.get().role).isEqualTo(Role.STAFF)
    }
}
```

**Technical details:**
- Use Flyway versioned migrations (V1__, V2__, etc.)
- PostgreSQL ENUM type for roles
- JPA entities with Kotlin data classes
- Indexed columns for performance (username, email, role, branch_id)

---

### Phase 3: Spring Security Basic Authentication
**Files**: 
- `src/main/kotlin/com/flowcare/backend/config/SecurityConfig.kt`
- `src/main/kotlin/com/flowcare/backend/auth/service/UserDetailsServiceImpl.kt`
- `src/main/kotlin/com/flowcare/backend/auth/model/UserPrincipal.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/config/SecurityConfigTest.kt`

Configure Spring Security to use HTTP Basic Authentication and implement UserDetailsService to load users from the database.

**Key code changes:**

```kotlin
// src/main/kotlin/com/flowcare/backend/auth/model/UserPrincipal.kt
package com.flowcare.backend.auth.model

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class UserPrincipal(
    private val user: User
) : UserDetails {
    
    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
    }
    
    override fun getPassword(): String = user.password
    
    override fun getUsername(): String = user.username
    
    override fun isAccountNonExpired(): Boolean = true
    
    override fun isAccountNonLocked(): Boolean = true
    
    override fun isCredentialsNonExpired(): Boolean = true
    
    override fun isEnabled(): Boolean = user.isActive
    
    fun getUser(): User = user
}
```

```kotlin
// src/main/kotlin/com/flowcare/backend/auth/service/UserDetailsServiceImpl.kt
package com.flowcare.backend.auth.service

import com.flowcare.backend.auth.model.UserPrincipal
import com.flowcare.backend.auth.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository
) : UserDetailsService {
    
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByUsername(username)
            .orElseThrow { UsernameNotFoundException("User not found: $username") }
        return UserPrincipal(user)
    }
}
```

```kotlin
// src/main/kotlin/com/flowcare/backend/config/SecurityConfig.kt
package com.flowcare.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
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
- Test UserDetailsService loads user correctly by username
- Test UserDetailsService throws exception for non-existent user
- Test UserPrincipal returns correct authorities based on role
- Test security configuration allows access to /api/health without auth
- Test security configuration blocks access to protected endpoints without auth

```kotlin
// src/test/kotlin/com/flowcare/backend/config/SecurityConfigTest.kt
package com.flowcare.backend.config

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun setup() {
        userRepository.deleteAll()
        val user = User(
            id = "test_admin",
            username = "admin",
            password = passwordEncoder.encode("password"),
            role = Role.ADMIN,
            fullName = "Admin User",
            email = "admin@test.com"
        )
        userRepository.save(user)
    }

    @Test
    fun `should allow access to health endpoint without authentication`() {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk)
    }

    @Test
    fun `should deny access to protected endpoint without authentication`() {
        mockMvc.perform(get("/api/protected"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `should allow access with valid credentials`() {
        mockMvc.perform(
            get("/api/protected")
                .with(httpBasic("admin", "password"))
        )
            .andExpect(status().isNotFound) // 404 because endpoint doesn't exist yet, but auth passed
    }
}
```

**Technical details:**
- BCrypt password encoding
- Stateless session management (no server-side sessions)
- CSRF disabled (appropriate for stateless API)
- Role-based authorities with ROLE_ prefix

---

### Phase 4: Health Check Endpoint and Error Handling
**Files**: 
- `src/main/kotlin/com/flowcare/backend/common/controller/HealthController.kt`
- `src/main/kotlin/com/flowcare/backend/common/dto/HealthResponse.kt`
- `src/main/kotlin/com/flowcare/backend/common/controller/TestController.kt`
- `src/main/kotlin/com/flowcare/backend/common/dto/TestResponse.kt`
- `src/main/kotlin/com/flowcare/backend/common/exception/GlobalExceptionHandler.kt`
- `src/main/kotlin/com/flowcare/backend/common/dto/ErrorResponse.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/common/controller/HealthControllerTest.kt`
- `src/test/kotlin/com/flowcare/backend/common/controller/TestControllerTest.kt`

Create a public health check endpoint, a protected test endpoint to verify authentication, and implement global exception handling for consistent error responses.

**Key code changes:**

```kotlin
// src/main/kotlin/com/flowcare/backend/common/dto/HealthResponse.kt
package com.flowcare.backend.common.dto

data class HealthResponse(
    val status: String,
    val timestamp: String
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/common/controller/HealthController.kt
package com.flowcare.backend.common.controller

import com.flowcare.backend.common.dto.HealthResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/health")
class HealthController {
    
    @GetMapping
    fun health(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(
            HealthResponse(
                status = "UP",
                timestamp = LocalDateTime.now().toString()
            )
        )
    }
}
```

```kotlin
// src/main/kotlin/com/flowcare/backend/common/dto/TestResponse.kt
package com.flowcare.backend.common.dto

data class TestResponse(
    val message: String,
    val username: String,
    val role: String
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/common/controller/TestController.kt
package com.flowcare.backend.common.controller

import com.flowcare.backend.auth.model.UserPrincipal
import com.flowcare.backend.common.dto.TestResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test")
class TestController {
    
    @GetMapping("/protected")
    fun protectedEndpoint(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<TestResponse> {
        return ResponseEntity.ok(
            TestResponse(
                message = "Access granted to protected endpoint",
                username = userPrincipal.username,
                role = userPrincipal.getUser().role.name
            )
        )
    }
}
```

```kotlin
// src/main/kotlin/com/flowcare/backend/common/dto/ErrorResponse.kt
package com.flowcare.backend.common.dto

data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: String
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/common/exception/GlobalExceptionHandler.kt
package com.flowcare.backend.common.exception

import com.flowcare.backend.common.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {
    
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: AuthenticationException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(
                ErrorResponse(
                    error = "Unauthorized",
                    message = ex.message ?: "Authentication failed",
                    timestamp = LocalDateTime.now().toString()
                )
            )
    }
    
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(
                ErrorResponse(
                    error = "Forbidden",
                    message = ex.message ?: "Access denied",
                    timestamp = LocalDateTime.now().toString()
                )
            )
    }
    
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ErrorResponse(
                    error = "Internal Server Error",
                    message = ex.message ?: "An unexpected error occurred",
                    timestamp = LocalDateTime.now().toString()
                )
            )
    }
}
```

**Test cases for this phase:**
- Test health endpoint returns 200 OK with correct response structure
- Test health endpoint is accessible without authentication
- Test protected endpoint returns 401 without authentication
- Test protected endpoint returns 200 with valid credentials and user info
- Test GlobalExceptionHandler returns 401 for authentication failures
- Test GlobalExceptionHandler returns 403 for access denied
- Test GlobalExceptionHandler returns 500 for generic exceptions

```kotlin
// src/test/kotlin/com/flowcare/backend/common/controller/HealthControllerTest.kt
package com.flowcare.backend.common.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `should return health status without authentication`() {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.timestamp").exists())
    }
}
```

```kotlin
// src/test/kotlin/com/flowcare/backend/common/controller/TestControllerTest.kt
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class TestControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun setup() {
        userRepository.deleteAll()
        val user = User(
            id = "test_admin",
            username = "admin",
            password = passwordEncoder.encode("password"),
            role = Role.ADMIN,
            fullName = "Admin User",
            email = "admin@test.com"
        )
        userRepository.save(user)
    }

    @Test
    fun `should deny access to protected endpoint without authentication`() {
        mockMvc.perform(get("/api/test/protected"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `should allow access to protected endpoint with valid credentials`() {
        mockMvc.perform(
            get("/api/test/protected")
                .with(httpBasic("admin", "password"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Access granted to protected endpoint"))
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.role").value("ADMIN"))
    }

    @Test
    fun `should deny access with invalid credentials`() {
        mockMvc.perform(
            get("/api/test/protected")
                .with(httpBasic("admin", "wrongpassword"))
        )
            .andExpect(status().isUnauthorized)
    }
}
```

**Technical details:**
- Public health endpoint for monitoring
- Protected test endpoint to verify authentication works end-to-end
- @AuthenticationPrincipal to access authenticated user details
- Consistent error response format across all endpoints
- Proper HTTP status codes (401, 403, 500)
- Timestamp in all responses for debugging

---

### Phase 5: Documentation and Setup Instructions
**Files**: 
- `README_SETUP.md`
- `docker-compose.yml` (for local PostgreSQL)
- `.env.example`

**Test Files**: None (documentation phase)

Create comprehensive setup documentation including environment setup, database configuration, and example API usage.

**Key code changes:**

```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: flowcare_postgres
    environment:
      POSTGRES_DB: flowcare_db
      POSTGRES_USER: flowcare_user
      POSTGRES_PASSWORD: flowcare_pass
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

```bash
# .env.example
# Database Configuration
DB_URL=jdbc:postgresql://localhost:5432/flowcare_db
DB_USERNAME=flowcare_user
DB_PASSWORD=flowcare_pass

# Application Configuration
SPRING_PROFILES_ACTIVE=dev
```

```markdown
# README_SETUP.md

## FlowCare Backend - Setup Instructions

### Prerequisites
- JDK 17 or higher
- Docker and Docker Compose (for PostgreSQL)
- Gradle 8.x (or use included wrapper)

### Database Setup

1. Start PostgreSQL using Docker Compose:
   ```bash
   docker-compose up -d
   ```

2. Verify PostgreSQL is running:
   ```bash
   docker ps
   ```

### Application Setup

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd rihal-backend-challenge
   ```

2. Copy environment configuration:
   ```bash
   cp .env.example .env
   ```

3. Build the project:
   ```bash
   ./gradlew build
   ```

4. Run database migrations (automatic on startup):
   ```bash
   ./gradlew bootRun
   ```

### Running the Application

Start the application:
```bash
./gradlew bootRun
```

The application will start on `http://localhost:8080`

### Verify Setup

Test the health endpoint:
```bash
curl http://localhost:8080/api/health
```

Expected response:
```json
{
  "status": "UP",
  "timestamp": "2026-03-13T15:20:00"
}
```

Test the protected endpoint (should fail without auth):
```bash
curl http://localhost:8080/api/test/protected
```

Expected response:
```json
{
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "timestamp": "2026-03-13T15:20:00"
}
```

Test the protected endpoint with authentication:
```bash
curl -u admin:Admin@123 http://localhost:8080/api/test/protected
```

Expected response (after seeding in Story 2):
```json
{
  "message": "Access granted to protected endpoint",
  "username": "admin",
  "role": "ADMIN"
}
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| DB_URL | PostgreSQL JDBC URL | jdbc:postgresql://localhost:5432/flowcare_db |
| DB_USERNAME | Database username | flowcare_user |
| DB_PASSWORD | Database password | flowcare_pass |
| SPRING_PROFILES_ACTIVE | Active Spring profile | dev |

### Running Tests

Execute all tests:
```bash
./gradlew test
```

### Database Migrations

Migrations are located in `src/main/resources/db/migration/`

Flyway automatically runs migrations on application startup.

To check migration status:
```bash
./gradlew flywayInfo
```

### Troubleshooting

**Issue: Database connection failed**
- Ensure PostgreSQL is running: `docker ps`
- Check database credentials in application-dev.yml

**Issue: Port 8080 already in use**
- Change server port in application.yml: `server.port: 8081`

**Issue: Flyway migration failed**
- Clean and rebuild: `./gradlew clean build`
- Check migration scripts for syntax errors
```

**Technical details:**
- Docker Compose for easy local PostgreSQL setup
- Environment variable examples
- Clear step-by-step setup instructions
- Troubleshooting section for common issues

---

## Technical Considerations

**Dependencies:**
- Spring Boot 3.2.3
- Kotlin 1.9.22
- PostgreSQL 15
- Flyway for migrations
- Spring Security for authentication
- Spring Data JPA with Hibernate

**Edge Cases:**
- Database connection failures (handle with clear error messages)
- Invalid credentials (return 401 Unauthorized)
- Missing environment variables (fail fast with descriptive error)

**Testing Strategy:**
- Unit tests for repositories using @DataJpaTest
- Integration tests for security configuration using @SpringBootTest
- Controller tests using MockMvc
- Test database isolation (each test uses clean state)

**Performance:**
- Database connection pooling (HikariCP default)
- Indexed columns for frequently queried fields
- Stateless authentication (no session overhead)

**Security:**
- BCrypt password hashing
- CSRF disabled for stateless API
- Basic Authentication over HTTPS (production)
- No sensitive data in logs

## Success Criteria

- [ ] Application starts successfully and connects to PostgreSQL
- [ ] Flyway migrations execute and create users table
- [ ] Health check endpoint returns 200 OK without authentication
- [ ] Protected test endpoint returns 401 without credentials
- [ ] Protected test endpoint returns 200 with valid Basic Auth credentials and user info
- [ ] Protected endpoints return 401 with invalid credentials
- [ ] User entity can be persisted and retrieved from database
- [ ] Spring Security loads users from database via UserDetailsService
- [ ] All tests pass successfully
- [ ] README_SETUP.md provides clear setup instructions
- [ ] Docker Compose file allows easy PostgreSQL setup
- [ ] .gitignore excludes build artifacts and IDE files
- [ ] settings.gradle.kts configures project name
