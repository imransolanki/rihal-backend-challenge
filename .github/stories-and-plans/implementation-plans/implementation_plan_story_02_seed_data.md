# Story 2: Seed System with Initial Data - Implementation Plan

## Overview
Implement automatic database seeding from example.json file on application startup. The seeding process must be idempotent (using PostgreSQL UPSERT), load all entities (branches, service types, staff, managers, slots, appointments, audit logs), hash passwords securely, and provide clear error feedback.

## Architecture
ApplicationRunner (startup) → SeedService → Parse example.json → UPSERT entities to PostgreSQL
- Branches → ServiceTypes → Users (Admin/Managers/Staff/Customers) → StaffServiceTypes → Slots → Appointments → AuditLogs
- BCryptPasswordEncoder for password hashing
- PostgreSQL INSERT ... ON CONFLICT for idempotency

## Implementation Phases

### Phase 1: Database Schema for All Entities
**Files**: 
- `src/main/resources/db/migration/V2__create_all_tables.sql`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/seed/SeedSchemaTest.kt`

Create comprehensive database schema for all FlowCare entities including branches, service types, slots, appointments, staff assignments, and audit logs.

**Key code changes:**

```sql
-- src/main/resources/db/migration/V2__create_all_tables.sql

-- Branches table
CREATE TABLE branches (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    city VARCHAR(100) NOT NULL,
    address VARCHAR(500) NOT NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Muscat',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_branches_city ON branches(city);
CREATE INDEX idx_branches_is_active ON branches(is_active);

-- Service Types table
CREATE TABLE service_types (
    id VARCHAR(50) PRIMARY KEY,
    branch_id VARCHAR(50) NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    duration_minutes INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_service_types_branch_id ON service_types(branch_id);
CREATE INDEX idx_service_types_is_active ON service_types(is_active);

-- Update users table to add foreign key to branches
ALTER TABLE users 
    ADD CONSTRAINT fk_users_branch 
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE SET NULL;

-- Staff Service Types (many-to-many)
CREATE TABLE staff_service_types (
    id SERIAL PRIMARY KEY,
    staff_id VARCHAR(50) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    service_type_id VARCHAR(50) NOT NULL REFERENCES service_types(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(staff_id, service_type_id)
);

CREATE INDEX idx_staff_service_types_staff_id ON staff_service_types(staff_id);
CREATE INDEX idx_staff_service_types_service_type_id ON staff_service_types(service_type_id);

-- Slots table
CREATE TABLE slots (
    id VARCHAR(50) PRIMARY KEY,
    branch_id VARCHAR(50) NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    service_type_id VARCHAR(50) NOT NULL REFERENCES service_types(id) ON DELETE CASCADE,
    staff_id VARCHAR(50) REFERENCES users(id) ON DELETE SET NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    capacity INTEGER NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT true,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_slots_branch_id ON slots(branch_id);
CREATE INDEX idx_slots_service_type_id ON slots(service_type_id);
CREATE INDEX idx_slots_staff_id ON slots(staff_id);
CREATE INDEX idx_slots_start_at ON slots(start_at);
CREATE INDEX idx_slots_deleted_at ON slots(deleted_at);

-- Appointment status enum
CREATE TYPE appointment_status AS ENUM ('BOOKED', 'CHECKED_IN', 'COMPLETED', 'NO_SHOW', 'CANCELLED');

-- Appointments table
CREATE TABLE appointments (
    id VARCHAR(50) PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    branch_id VARCHAR(50) NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    service_type_id VARCHAR(50) NOT NULL REFERENCES service_types(id) ON DELETE CASCADE,
    slot_id VARCHAR(50) REFERENCES slots(id) ON DELETE SET NULL,
    staff_id VARCHAR(50) REFERENCES users(id) ON DELETE SET NULL,
    status appointment_status NOT NULL DEFAULT 'BOOKED',
    attachment_path VARCHAR(500),
    internal_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_appointments_customer_id ON appointments(customer_id);
CREATE INDEX idx_appointments_branch_id ON appointments(branch_id);
CREATE INDEX idx_appointments_slot_id ON appointments(slot_id);
CREATE INDEX idx_appointments_staff_id ON appointments(staff_id);
CREATE INDEX idx_appointments_status ON appointments(status);
CREATE INDEX idx_appointments_created_at ON appointments(created_at);

-- Audit Logs table
CREATE TABLE audit_logs (
    id VARCHAR(50) PRIMARY KEY,
    actor_id VARCHAR(50) NOT NULL,
    actor_role VARCHAR(50) NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_actor_id ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action_type ON audit_logs(action_type);
CREATE INDEX idx_audit_logs_entity_type ON audit_logs(entity_type);
CREATE INDEX idx_audit_logs_entity_id ON audit_logs(entity_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp);

-- System configuration table for retention period
CREATE TABLE system_config (
    key VARCHAR(100) PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_config (key, value, description) 
VALUES ('soft_delete_retention_days', '30', 'Number of days to retain soft-deleted records before hard delete')
ON CONFLICT (key) DO NOTHING;
```

**Test cases for this phase:**
- Test migration V2 executes successfully
- Test all tables are created with correct columns
- Test foreign key constraints are properly set up
- Test indexes are created for performance
- Test system_config table has default retention period

```kotlin
// src/test/kotlin/com/flowcare/backend/seed/SeedSchemaTest.kt
package com.flowcare.backend.seed

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.assertj.core.api.Assertions.assertThat

@SpringBootTest
class SeedSchemaTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `should have all required tables created`() {
        val tables = listOf(
            "branches", "service_types", "staff_service_types",
            "slots", "appointments", "audit_logs", "system_config"
        )
        
        tables.forEach { table ->
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Int::class.java,
                table
            )
            assertThat(count).isEqualTo(1)
        }
    }

    @Test
    fun `should have default retention period configured`() {
        val value = jdbcTemplate.queryForObject(
            "SELECT value FROM system_config WHERE key = 'soft_delete_retention_days'",
            String::class.java
        )
        assertThat(value).isEqualTo("30")
    }
}
```

**Technical details:**
- All entities in single migration to ensure proper dependency order
- Foreign keys with appropriate ON DELETE actions (CASCADE or SET NULL)
- Indexes on frequently queried columns
- JSONB for audit log metadata
- TIMESTAMP WITH TIME ZONE for proper timezone handling
- Default system configuration for retention period


---

### Phase 2: JPA Entities for All Domain Models
**Files**: 
- `src/main/kotlin/com/flowcare/backend/branch/model/Branch.kt`
- `src/main/kotlin/com/flowcare/backend/service/model/ServiceType.kt`
- `src/main/kotlin/com/flowcare/backend/slot/model/Slot.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/model/Appointment.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/model/AppointmentStatus.kt`
- `src/main/kotlin/com/flowcare/backend/audit/model/AuditLog.kt`
- `src/main/kotlin/com/flowcare/backend/staff/model/StaffServiceType.kt`
- `src/main/kotlin/com/flowcare/backend/config/model/SystemConfig.kt`

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/branch/model/BranchTest.kt`

Create JPA entity classes for all domain models matching the database schema.

**Key code changes:**

```kotlin
// src/main/kotlin/com/flowcare/backend/branch/model/Branch.kt
package com.flowcare.backend.branch.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "branches")
data class Branch(
    @Id
    val id: String,
    
    @Column(nullable = false)
    val name: String,
    
    @Column(nullable = false)
    val city: String,
    
    @Column(nullable = false)
    val address: String,
    
    @Column(nullable = false)
    val timezone: String = "Asia/Muscat",
    
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/service/model/ServiceType.kt
package com.flowcare.backend.service.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "service_types")
data class ServiceType(
    @Id
    val id: String,
    
    @Column(name = "branch_id", nullable = false)
    val branchId: String,
    
    @Column(nullable = false)
    val name: String,
    
    @Column(columnDefinition = "TEXT")
    val description: String? = null,
    
    @Column(name = "duration_minutes", nullable = false)
    val durationMinutes: Int,
    
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/slot/model/Slot.kt
package com.flowcare.backend.slot.model

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.time.LocalDateTime

@Entity
@Table(name = "slots")
data class Slot(
    @Id
    val id: String,
    
    @Column(name = "branch_id", nullable = false)
    val branchId: String,
    
    @Column(name = "service_type_id", nullable = false)
    val serviceTypeId: String,
    
    @Column(name = "staff_id")
    val staffId: String? = null,
    
    @Column(name = "start_at", nullable = false)
    val startAt: OffsetDateTime,
    
    @Column(name = "end_at", nullable = false)
    val endAt: OffsetDateTime,
    
    @Column(nullable = false)
    val capacity: Int = 1,
    
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
    
    @Column(name = "deleted_at")
    var deletedAt: OffsetDateTime? = null,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/appointment/model/AppointmentStatus.kt
package com.flowcare.backend.appointment.model

enum class AppointmentStatus {
    BOOKED,
    CHECKED_IN,
    COMPLETED,
    NO_SHOW,
    CANCELLED
}
```

```kotlin
// src/main/kotlin/com/flowcare/backend/appointment/model/Appointment.kt
package com.flowcare.backend.appointment.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "appointments")
data class Appointment(
    @Id
    val id: String,
    
    @Column(name = "customer_id", nullable = false)
    val customerId: String,
    
    @Column(name = "branch_id", nullable = false)
    val branchId: String,
    
    @Column(name = "service_type_id", nullable = false)
    val serviceTypeId: String,
    
    @Column(name = "slot_id")
    val slotId: String? = null,
    
    @Column(name = "staff_id")
    val staffId: String? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: AppointmentStatus = AppointmentStatus.BOOKED,
    
    @Column(name = "attachment_path")
    val attachmentPath: String? = null,
    
    @Column(name = "internal_notes", columnDefinition = "TEXT")
    var internalNotes: String? = null,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/audit/model/AuditLog.kt
package com.flowcare.backend.audit.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.time.LocalDateTime

@Entity
@Table(name = "audit_logs")
data class AuditLog(
    @Id
    val id: String,
    
    @Column(name = "actor_id", nullable = false)
    val actorId: String,
    
    @Column(name = "actor_role", nullable = false)
    val actorRole: String,
    
    @Column(name = "action_type", nullable = false)
    val actionType: String,
    
    @Column(name = "entity_type", nullable = false)
    val entityType: String,
    
    @Column(name = "entity_id", nullable = false)
    val entityId: String,
    
    @Column(nullable = false)
    val timestamp: OffsetDateTime,
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val metadata: Map<String, Any>? = null,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/staff/model/StaffServiceType.kt
package com.flowcare.backend.staff.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "staff_service_types")
data class StaffServiceType(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(name = "staff_id", nullable = false)
    val staffId: String,
    
    @Column(name = "service_type_id", nullable = false)
    val serviceTypeId: String,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/config/model/SystemConfig.kt
package com.flowcare.backend.config.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "system_config")
data class SystemConfig(
    @Id
    val key: String,
    
    @Column(nullable = false, columnDefinition = "TEXT")
    var value: String,
    
    @Column(columnDefinition = "TEXT")
    val description: String? = null,
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

**Test cases for this phase:**
- Test Branch entity can be persisted and retrieved
- Test ServiceType entity with branch relationship
- Test Slot entity with timezone handling
- Test Appointment entity with status enum
- Test AuditLog entity with JSONB metadata
- Test StaffServiceType entity with auto-generated ID

```kotlin
// src/test/kotlin/com/flowcare/backend/branch/model/BranchTest.kt
package com.flowcare.backend.branch.model

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.assertj.core.api.Assertions.assertThat

@DataJpaTest
class BranchTest {

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Test
    fun `should persist and retrieve branch`() {
        val branch = Branch(
            id = "br_test_001",
            name = "Test Branch",
            city = "Muscat",
            address = "Test Address",
            timezone = "Asia/Muscat"
        )
        
        entityManager.persist(branch)
        entityManager.flush()
        
        val found = entityManager.find(Branch::class.java, "br_test_001")
        assertThat(found).isNotNull
        assertThat(found.name).isEqualTo("Test Branch")
        assertThat(found.city).isEqualTo("Muscat")
    }
}
```

**Technical details:**
- Use OffsetDateTime for timezone-aware timestamps
- JSONB support via @JdbcTypeCode annotation
- Enum mapping for AppointmentStatus
- Auto-generated ID for StaffServiceType (junction table)
- Immutable data classes with nullable fields where appropriate


---

### Phase 3: Repositories and Seed Data DTOs
**Files**: 
- `src/main/kotlin/com/flowcare/backend/branch/repository/BranchRepository.kt`
- `src/main/kotlin/com/flowcare/backend/service/repository/ServiceTypeRepository.kt`
- `src/main/kotlin/com/flowcare/backend/slot/repository/SlotRepository.kt`
- `src/main/kotlin/com/flowcare/backend/appointment/repository/AppointmentRepository.kt`
- `src/main/kotlin/com/flowcare/backend/audit/repository/AuditLogRepository.kt`
- `src/main/kotlin/com/flowcare/backend/staff/repository/StaffServiceTypeRepository.kt`
- `src/main/kotlin/com/flowcare/backend/seed/dto/SeedData.kt`
- `src/main/resources/data/seed.json` (copy from example.json)

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/seed/dto/SeedDataTest.kt`

Create Spring Data JPA repositories for all entities and DTOs to parse the seed.json file.

**Key code changes:**

```kotlin
// src/main/kotlin/com/flowcare/backend/branch/repository/BranchRepository.kt
package com.flowcare.backend.branch.repository

import com.flowcare.backend.branch.model.Branch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BranchRepository : JpaRepository<Branch, String>
```

```kotlin
// src/main/kotlin/com/flowcare/backend/service/repository/ServiceTypeRepository.kt
package com.flowcare.backend.service.repository

import com.flowcare.backend.service.model.ServiceType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ServiceTypeRepository : JpaRepository<ServiceType, String>
```

```kotlin
// src/main/kotlin/com/flowcare/backend/slot/repository/SlotRepository.kt
package com.flowcare.backend.slot.repository

import com.flowcare.backend.slot.model.Slot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SlotRepository : JpaRepository<Slot, String>
```

```kotlin
// src/main/kotlin/com/flowcare/backend/appointment/repository/AppointmentRepository.kt
package com.flowcare.backend.appointment.repository

import com.flowcare.backend.appointment.model.Appointment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AppointmentRepository : JpaRepository<Appointment, String>
```

```kotlin
// src/main/kotlin/com/flowcare/backend/audit/repository/AuditLogRepository.kt
package com.flowcare.backend.audit.repository

import com.flowcare.backend.audit.model.AuditLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, String>
```

```kotlin
// src/main/kotlin/com/flowcare/backend/staff/repository/StaffServiceTypeRepository.kt
package com.flowcare.backend.staff.repository

import com.flowcare.backend.staff.model.StaffServiceType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StaffServiceTypeRepository : JpaRepository<StaffServiceType, Long> {
    fun existsByStaffIdAndServiceTypeId(staffId: String, serviceTypeId: String): Boolean
}
```

```kotlin
// src/main/kotlin/com/flowcare/backend/seed/dto/SeedData.kt
package com.flowcare.backend.seed.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class SeedData(
    val users: Users,
    val branches: List<BranchDto>,
    @JsonProperty("service_types") val serviceTypes: List<ServiceTypeDto>,
    @JsonProperty("staff_service_types") val staffServiceTypes: List<StaffServiceTypeDto>,
    val slots: List<SlotDto>,
    val appointments: List<AppointmentDto>,
    @JsonProperty("audit_logs") val auditLogs: List<AuditLogDto>
)

data class Users(
    val admin: List<UserDto>,
    @JsonProperty("branch_managers") val branchManagers: List<UserDto>,
    val staff: List<UserDto>,
    val customers: List<UserDto>
)

data class UserDto(
    val id: String,
    val username: String,
    val password: String,
    val role: String,
    @JsonProperty("full_name") val fullName: String,
    val email: String,
    val phone: String? = null,
    @JsonProperty("branch_id") val branchId: String? = null,
    @JsonProperty("is_active") val isActive: Boolean = true
)

data class BranchDto(
    val id: String,
    val name: String,
    val city: String,
    val address: String,
    val timezone: String,
    @JsonProperty("is_active") val isActive: Boolean = true
)

data class ServiceTypeDto(
    val id: String,
    @JsonProperty("branch_id") val branchId: String,
    val name: String,
    val description: String,
    @JsonProperty("duration_minutes") val durationMinutes: Int,
    @JsonProperty("is_active") val isActive: Boolean = true
)

data class StaffServiceTypeDto(
    @JsonProperty("staff_id") val staffId: String,
    @JsonProperty("service_type_id") val serviceTypeId: String
)

data class SlotDto(
    val id: String,
    @JsonProperty("branch_id") val branchId: String,
    @JsonProperty("service_type_id") val serviceTypeId: String,
    @JsonProperty("staff_id") val staffId: String? = null,
    @JsonProperty("start_at") val startAt: String,
    @JsonProperty("end_at") val endAt: String,
    val capacity: Int = 1,
    @JsonProperty("is_active") val isActive: Boolean = true
)

data class AppointmentDto(
    val id: String,
    @JsonProperty("customer_id") val customerId: String,
    @JsonProperty("branch_id") val branchId: String,
    @JsonProperty("service_type_id") val serviceTypeId: String,
    @JsonProperty("slot_id") val slotId: String,
    @JsonProperty("staff_id") val staffId: String? = null,
    val status: String,
    @JsonProperty("created_at") val createdAt: String
)

data class AuditLogDto(
    val id: String,
    @JsonProperty("actor_id") val actorId: String,
    @JsonProperty("actor_role") val actorRole: String,
    @JsonProperty("action_type") val actionType: String,
    @JsonProperty("entity_type") val entityType: String,
    @JsonProperty("entity_id") val entityId: String,
    val timestamp: String,
    val metadata: Map<String, Any>? = null
)
```

**Test cases for this phase:**
- Test seed.json can be parsed into SeedData DTO
- Test all nested structures are correctly mapped
- Test Jackson annotations handle snake_case to camelCase conversion
- Test optional fields are handled correctly

```kotlin
// src/test/kotlin/com/flowcare/backend/seed/dto/SeedDataTest.kt
package com.flowcare.backend.seed.dto

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.assertj.core.api.Assertions.assertThat

class SeedDataTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `should parse seed json file`() {
        val resource = ClassPathResource("data/seed.json")
        val seedData: SeedData = objectMapper.readValue(resource.inputStream)
        
        assertThat(seedData.users.admin).isNotEmpty
        assertThat(seedData.branches).hasSize(2)
        assertThat(seedData.serviceTypes).hasSizeGreaterThanOrEqualTo(6)
        assertThat(seedData.slots).hasSizeGreaterThanOrEqualTo(10)
    }

    @Test
    fun `should correctly map user roles`() {
        val resource = ClassPathResource("data/seed.json")
        val seedData: SeedData = objectMapper.readValue(resource.inputStream)
        
        assertThat(seedData.users.admin.first().role).isEqualTo("ADMIN")
        assertThat(seedData.users.branchManagers.first().role).isEqualTo("BRANCH_MANAGER")
    }
}
```

**Technical details:**
- Jackson annotations for JSON field mapping (snake_case to camelCase)
- Nested data structures for users (admin, managers, staff, customers)
- Copy example.json to src/main/resources/data/seed.json
- All DTOs are immutable data classes


---

### Phase 4: Seed Service with UPSERT Logic
**Files**: 
- `src/main/kotlin/com/flowcare/backend/seed/service/SeedService.kt`
- `src/main/kotlin/com/flowcare/backend/seed/runner/SeedDataRunner.kt`
- `src/main/resources/application.yml` (add seed.enabled property)

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/seed/service/SeedServiceTest.kt`

Implement the seeding service that reads seed.json, hashes passwords, and uses native SQL UPSERT (INSERT ... ON CONFLICT) for idempotency.

**Key code changes:**

```yaml
# Add to src/main/resources/application.yml
seed:
  enabled: true
  file-path: classpath:data/seed.json
```

```kotlin
// src/main/kotlin/com/flowcare/backend/seed/service/SeedService.kt
package com.flowcare.backend.seed.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.flowcare.backend.seed.dto.*
import org.slf4j.LoggerFactory
import org.springframework.core.io.ResourceLoader
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
class SeedService(
    private val jdbcTemplate: JdbcTemplate,
    private val passwordEncoder: PasswordEncoder,
    private val resourceLoader: ResourceLoader
) {
    private val logger = LoggerFactory.getLogger(SeedService::class.java)
    private val objectMapper = jacksonObjectMapper()

    @Transactional
    fun seedDatabase(filePath: String) {
        try {
            logger.info("Starting database seeding from: $filePath")
            
            val resource = resourceLoader.getResource(filePath)
            val seedData: SeedData = objectMapper.readValue(resource.inputStream)
            
            seedBranches(seedData.branches)
            seedServiceTypes(seedData.serviceTypes)
            seedUsers(seedData.users)
            seedStaffServiceTypes(seedData.staffServiceTypes)
            seedSlots(seedData.slots)
            seedAppointments(seedData.appointments)
            seedAuditLogs(seedData.auditLogs)
            
            logger.info("Database seeding completed successfully")
        } catch (e: Exception) {
            logger.error("Error during database seeding: ${e.message}", e)
            throw RuntimeException("Failed to seed database", e)
        }
    }

    private fun seedBranches(branches: List<BranchDto>) {
        logger.info("Seeding ${branches.size} branches")
        branches.forEach { branch ->
            jdbcTemplate.update(
                """
                INSERT INTO branches (id, name, city, address, timezone, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """,
                branch.id, branch.name, branch.city, branch.address, 
                branch.timezone, branch.isActive
            )
        }
        logger.info("Branches seeded successfully")
    }

    private fun seedServiceTypes(serviceTypes: List<ServiceTypeDto>) {
        logger.info("Seeding ${serviceTypes.size} service types")
        serviceTypes.forEach { service ->
            jdbcTemplate.update(
                """
                INSERT INTO service_types (id, branch_id, name, description, duration_minutes, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """,
                service.id, service.branchId, service.name, service.description,
                service.durationMinutes, service.isActive
            )
        }
        logger.info("Service types seeded successfully")
    }

    private fun seedUsers(users: Users) {
        val allUsers = users.admin + users.branchManagers + users.staff + users.customers
        logger.info("Seeding ${allUsers.size} users")
        
        allUsers.forEach { user ->
            val hashedPassword = passwordEncoder.encode(user.password)
            jdbcTemplate.update(
                """
                INSERT INTO users (id, username, password, role, full_name, email, phone, branch_id, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?::user_role, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """,
                user.id, user.username, hashedPassword, user.role,
                user.fullName, user.email, user.phone, user.branchId, user.isActive
            )
        }
        logger.info("Users seeded successfully")
    }

    private fun seedStaffServiceTypes(assignments: List<StaffServiceTypeDto>) {
        logger.info("Seeding ${assignments.size} staff-service assignments")
        assignments.forEach { assignment ->
            jdbcTemplate.update(
                """
                INSERT INTO staff_service_types (staff_id, service_type_id, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (staff_id, service_type_id) DO NOTHING
                """,
                assignment.staffId, assignment.serviceTypeId
            )
        }
        logger.info("Staff-service assignments seeded successfully")
    }

    private fun seedSlots(slots: List<SlotDto>) {
        logger.info("Seeding ${slots.size} slots")
        slots.forEach { slot ->
            val startAt = OffsetDateTime.parse(slot.startAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val endAt = OffsetDateTime.parse(slot.endAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            
            jdbcTemplate.update(
                """
                INSERT INTO slots (id, branch_id, service_type_id, staff_id, start_at, end_at, capacity, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """,
                slot.id, slot.branchId, slot.serviceTypeId, slot.staffId,
                startAt, endAt, slot.capacity, slot.isActive
            )
        }
        logger.info("Slots seeded successfully")
    }

    private fun seedAppointments(appointments: List<AppointmentDto>) {
        logger.info("Seeding ${appointments.size} appointments")
        appointments.forEach { appointment ->
            val createdAt = OffsetDateTime.parse(appointment.createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            
            jdbcTemplate.update(
                """
                INSERT INTO appointments (id, customer_id, branch_id, service_type_id, slot_id, staff_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::appointment_status, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """,
                appointment.id, appointment.customerId, appointment.branchId,
                appointment.serviceTypeId, appointment.slotId, appointment.staffId,
                appointment.status, createdAt
            )
        }
        logger.info("Appointments seeded successfully")
    }

    private fun seedAuditLogs(auditLogs: List<AuditLogDto>) {
        logger.info("Seeding ${auditLogs.size} audit logs")
        auditLogs.forEach { log ->
            val timestamp = OffsetDateTime.parse(log.timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val metadataJson = log.metadata?.let { objectMapper.writeValueAsString(it) }
            
            jdbcTemplate.update(
                """
                INSERT INTO audit_logs (id, actor_id, actor_role, action_type, entity_type, entity_id, timestamp, metadata, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """,
                log.id, log.actorId, log.actorRole, log.actionType,
                log.entityType, log.entityId, timestamp, metadataJson
            )
        }
        logger.info("Audit logs seeded successfully")
    }
}
```

```kotlin
// src/main/kotlin/com/flowcare/backend/seed/runner/SeedDataRunner.kt
package com.flowcare.backend.seed.runner

import com.flowcare.backend.seed.service.SeedService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["seed.enabled"], havingValue = "true", matchIfMissing = false)
class SeedDataRunner(
    private val seedService: SeedService
) : ApplicationRunner {
    
    private val logger = LoggerFactory.getLogger(SeedDataRunner::class.java)

    override fun run(args: ApplicationArguments) {
        logger.info("Seed data runner triggered")
        seedService.seedDatabase("classpath:data/seed.json")
    }
}
```

**Test cases for this phase:**
- Test seeding runs successfully with valid seed.json
- Test idempotency - running seed twice doesn't create duplicates
- Test passwords are hashed correctly
- Test error handling when seed file is invalid
- Test seeding can be disabled via configuration

```kotlin
// src/test/kotlin/com/flowcare/backend/seed/service/SeedServiceTest.kt
package com.flowcare.backend.seed.service

import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.branch.repository.BranchRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.assertj.core.api.Assertions.assertThat

@SpringBootTest
class SeedServiceTest {

    @Autowired
    private lateinit var seedService: SeedService

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `should seed database successfully`() {
        seedService.seedDatabase("classpath:data/seed.json")
        
        val branches = branchRepository.findAll()
        assertThat(branches).hasSizeGreaterThanOrEqualTo(2)
        
        val users = userRepository.findAll()
        assertThat(users).isNotEmpty
    }

    @Test
    fun `should be idempotent - no duplicates on second run`() {
        seedService.seedDatabase("classpath:data/seed.json")
        val firstCount = branchRepository.count()
        
        seedService.seedDatabase("classpath:data/seed.json")
        val secondCount = branchRepository.count()
        
        assertThat(firstCount).isEqualTo(secondCount)
    }

    @Test
    fun `should hash passwords correctly`() {
        seedService.seedDatabase("classpath:data/seed.json")
        
        val admin = userRepository.findByUsername("admin").orElseThrow()
        assertThat(passwordEncoder.matches("Admin@123", admin.password)).isTrue
    }
}
```

**Technical details:**
- Native SQL with INSERT ... ON CONFLICT for idempotency
- BCryptPasswordEncoder for password hashing
- Proper timezone handling with OffsetDateTime
- JSONB casting for audit log metadata
- Enum casting for user_role and appointment_status
- Conditional bean loading via @ConditionalOnProperty
- Comprehensive error logging


---

### Phase 5: Update README and Verification
**Files**: 
- `README_SETUP.md` (update with seeding instructions)
- `src/main/resources/data/seed.json` (copy from example.json)

**Test Files**: None (documentation phase)

Update documentation with seeding instructions and copy example.json to the resources directory.

**Key code changes:**

Add to README_SETUP.md:

```markdown
### Database Seeding

The application automatically seeds the database on startup with initial data including:
- 2 branches (Muscat, Suhar)
- 6 service types (3 per branch)
- 1 Admin user
- 2 Branch Managers
- 4 Staff members
- 3 Customer accounts
- 14+ appointment slots
- 2 sample appointments
- Audit log entries

#### Seeding Configuration

Seeding is controlled via `application.yml`:

```yaml
seed:
  enabled: true  # Set to false to disable seeding
  file-path: classpath:data/seed.json
```

#### Verify Seeding

After starting the application, verify seeding was successful:

```bash
# Check branches
curl -u admin:Admin@123 http://localhost:8080/api/branches

# Check users (admin credentials from seed data)
curl -u admin:Admin@123 http://localhost:8080/api/test/protected
```

Expected response:
```json
{
  "message": "Access granted to protected endpoint",
  "username": "admin",
  "role": "ADMIN"
}
```

#### Seed Data Credentials

The seed file includes these test accounts:

| Username | Password | Role |
|----------|----------|------|
| admin | Admin@123 | ADMIN |
| mgr_muscat | Manager@123 | BRANCH_MANAGER |
| mgr_suhar | Manager@123 | BRANCH_MANAGER |
| staff_muscat_1 | Staff@123 | STAFF |
| staff_muscat_2 | Staff@123 | STAFF |
| staff_suhar_1 | Staff@123 | STAFF |
| staff_suhar_2 | Staff@123 | STAFF |
| cust_ahmed | Customer@123 | CUSTOMER |
| cust_fatima | Customer@123 | CUSTOMER |
| cust_khalid | Customer@123 | CUSTOMER |

#### Idempotency

Seeding is idempotent - running the application multiple times will not create duplicate records. The system uses PostgreSQL's `INSERT ... ON CONFLICT DO NOTHING` to skip existing records.

#### Disable Seeding

To disable automatic seeding (e.g., in production):

```yaml
# application-prod.yml
seed:
  enabled: false
```

Or via environment variable:
```bash
SEED_ENABLED=false ./gradlew bootRun
```

#### Troubleshooting Seeding

**Issue: Seeding fails with foreign key constraint error**
- Ensure PostgreSQL is running and migrations have executed
- Check that V2__create_all_tables.sql migration completed successfully

**Issue: Passwords don't work**
- Verify BCryptPasswordEncoder is configured in SecurityConfig
- Check application logs for password hashing errors

**Issue: Duplicate key errors**
- This shouldn't happen due to ON CONFLICT, but if it does, check that IDs in seed.json are unique
```

**Technical details:**
- Copy example.json to src/main/resources/data/seed.json
- Document all seeded credentials for testing
- Explain idempotency mechanism
- Provide troubleshooting guidance


---

## Technical Considerations

**Dependencies:**
- Story 1 must be complete (database, JPA, Spring Security)
- Jackson for JSON parsing
- JdbcTemplate for native SQL UPSERT
- BCryptPasswordEncoder for password hashing

**Edge Cases:**
- Invalid JSON format in seed file (catch and log error)
- Missing required fields in seed data (validation)
- Foreign key constraint violations (proper seeding order)
- Timezone parsing errors (handle different formats)
- Duplicate IDs in seed file (ON CONFLICT handles this)

**Testing Strategy:**
- Unit tests for DTO parsing
- Integration tests for seeding service
- Idempotency tests (run seed twice, verify counts)
- Password hashing verification tests
- Test with seed.enabled=false to verify conditional loading

**Performance:**
- Batch inserts for better performance (future optimization)
- Transaction wrapping ensures atomicity
- ON CONFLICT is efficient for idempotency checks

**Security:**
- Passwords are hashed before storage
- Seed credentials are for development/testing only
- Production should disable seeding or use different credentials

## Success Criteria

- [ ] V2 migration creates all required tables successfully
- [ ] All JPA entities can be persisted and retrieved
- [ ] seed.json file is parsed correctly into DTOs
- [ ] Seeding runs automatically on application startup
- [ ] All branches from seed file are created (minimum 2)
- [ ] All service types are created and associated with branches (minimum 6)
- [ ] All users are created with correct roles (Admin, Managers, Staff, Customers)
- [ ] Passwords are hashed using BCrypt
- [ ] Staff-service assignments are created correctly
- [ ] Slots are created with proper timezone handling (minimum 10)
- [ ] Appointments are created and linked to slots
- [ ] Audit logs are seeded with JSONB metadata
- [ ] Seeding is idempotent - running twice doesn't create duplicates
- [ ] Seeding can be disabled via seed.enabled=false configuration
- [ ] Clear error messages are logged if seeding fails
- [ ] Admin user can authenticate with seeded credentials
- [ ] README documents seeding process and test credentials
- [ ] All tests pass successfully
