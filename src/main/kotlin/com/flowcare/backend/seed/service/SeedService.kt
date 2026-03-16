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
    private val log = LoggerFactory.getLogger(SeedService::class.java)
    private val objectMapper = jacksonObjectMapper()

    @Transactional
    fun seedDatabase(filePath: String) {
        log.info("Starting database seeding from: {}", filePath)

        val resource = resourceLoader.getResource(filePath)
        val seedData: SeedData = objectMapper.readValue(resource.inputStream)

        seedBranches(seedData.branches)
        seedServiceTypes(seedData.serviceTypes)
        seedUsers(seedData.users)
        seedStaffServiceTypes(seedData.staffServiceTypes)
        seedSlots(seedData.slots)
        seedAppointments(seedData.appointments)
        seedAuditLogs(seedData.auditLogs)

        log.info("Database seeding completed successfully")
    }

    private fun seedBranches(branches: List<BranchDto>) {
        log.info("Seeding {} branches", branches.size)
        branches.forEach { b ->
            jdbcTemplate.update(
                """INSERT INTO branches (id, name, city, address, timezone, is_active)
                   VALUES (?, ?, ?, ?, ?, ?)
                   ON CONFLICT (id) DO NOTHING""",
                b.id, b.name, b.city, b.address, b.timezone, b.isActive
            )
        }
    }

    private fun seedServiceTypes(serviceTypes: List<ServiceTypeDto>) {
        log.info("Seeding {} service types", serviceTypes.size)
        serviceTypes.forEach { s ->
            jdbcTemplate.update(
                """INSERT INTO service_types (id, branch_id, name, description, duration_minutes, is_active)
                   VALUES (?, ?, ?, ?, ?, ?)
                   ON CONFLICT (id) DO NOTHING""",
                s.id, s.branchId, s.name, s.description, s.durationMinutes, s.isActive
            )
        }
    }

    private fun seedUsers(users: Users) {
        val allUsers = users.admin + users.branchManagers + users.staff + users.customers
        log.info("Seeding {} users", allUsers.size)
        allUsers.forEach { u ->
            val hashedPassword = passwordEncoder.encode(u.password)
            // Remove any conflicting user with same username but different id
            jdbcTemplate.update(
                "DELETE FROM users WHERE username = ? AND id != ?",
                u.username, u.id
            )
            jdbcTemplate.update(
                """INSERT INTO users (id, username, password, role, full_name, email, phone, branch_id, is_active)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT (id) DO NOTHING""",
                u.id, u.username, hashedPassword, u.role,
                u.fullName, u.email, u.phone, u.branchId, u.isActive
            )
        }
    }

    private fun seedStaffServiceTypes(assignments: List<StaffServiceTypeDto>) {
        log.info("Seeding {} staff-service assignments", assignments.size)
        assignments.forEach { a ->
            jdbcTemplate.update(
                """INSERT INTO staff_service_types (staff_id, service_type_id)
                   VALUES (?, ?)
                   ON CONFLICT (staff_id, service_type_id) DO NOTHING""",
                a.staffId, a.serviceTypeId
            )
        }
    }

    private fun seedSlots(slots: List<SlotDto>) {
        log.info("Seeding {} slots", slots.size)
        slots.forEach { s ->
            jdbcTemplate.update(
                """INSERT INTO slots (id, branch_id, service_type_id, staff_id, start_at, end_at, capacity, booked_count, is_active)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT (id) DO NOTHING""",
                s.id, s.branchId, s.serviceTypeId, s.staffId,
                OffsetDateTime.parse(s.startAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                OffsetDateTime.parse(s.endAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                s.capacity, s.bookedCount, s.isActive
            )
        }
    }

    private fun seedAppointments(appointments: List<AppointmentDto>) {
        log.info("Seeding {} appointments", appointments.size)
        appointments.forEach { a ->
            jdbcTemplate.update(
                """INSERT INTO appointments (id, customer_id, branch_id, service_type_id, slot_id, staff_id, status, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT (id) DO NOTHING""",
                a.id, a.customerId, a.branchId, a.serviceTypeId, a.slotId, a.staffId,
                a.status, OffsetDateTime.parse(a.createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            )
        }
    }

    private fun seedAuditLogs(auditLogs: List<AuditLogDto>) {
        log.info("Seeding {} audit logs", auditLogs.size)
        auditLogs.forEach { l ->
            val metadataJson = l.metadata?.let { objectMapper.writeValueAsString(it) }
            jdbcTemplate.update(
                """INSERT INTO audit_logs (id, actor_id, actor_role, action_type, entity_type, entity_id, timestamp, metadata)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                   ON CONFLICT (id) DO NOTHING""",
                l.id, l.actorId, l.actorRole, l.actionType, l.entityType, l.entityId,
                OffsetDateTime.parse(l.timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                metadataJson
            )
        }
    }
}
