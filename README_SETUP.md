# FlowCare Backend - Setup & Run Guide

## Prerequisites
- JDK 17+
- Docker & Docker Compose
- Gradle 8.x (or use `./gradlew` wrapper)

## Quick Start

```bash
# 1. Start PostgreSQL
docker-compose up -d

# 2. Run the application
./gradlew bootRun

# 3. Verify
curl http://localhost:8080/api/health
```

The app starts on port `8080`. Seed data is loaded automatically on first run.

## Running Tests

```bash
./gradlew clean test
```

## Seed Data Credentials

| Role | Username | Password |
|------|----------|----------|
| Admin | `admin` | `Admin@123` |
| Branch Manager | `mgr_muscat` | `Manager@123` |
| Staff | `staff_muscat_1` | `Staff@123` |
| Customer | `cust_ahmed` | `Customer@123` |

## Authentication

All protected endpoints use HTTP Basic Auth:

```bash
curl -u admin:Admin@123 http://localhost:8080/api/staff
```

## Project Structure

```
src/main/kotlin/com/flowcare/backend/
├── appointment/     # Booking, cancellation, rescheduling
├── audit/           # Audit log viewing & CSV export
├── auth/            # Registration, authentication, customer management
├── branch/          # Branch & service type discovery
├── common/          # Health check, error handling
├── config/          # System configuration (retention period)
├── seed/            # Seed data loader
├── service/         # Service type models
├── slot/            # Slot management, public discovery, cleanup
├── staff/           # Staff listing
└── storage/         # File storage for ID documents
```

## What's Implemented

### Core Infrastructure
- Kotlin + Spring Boot 3 with PostgreSQL and Flyway migrations
- Seed data auto-loaded on startup (branches, services, staff, customers, slots)
- HTTP Basic Auth with role-based access control (ADMIN, BRANCH_MANAGER, STAFF, CUSTOMER)
- Global exception handling with structured error responses
- Audit logging for all sensitive actions (bookings, cancellations, schedule changes)

### Customer-Facing
- **Registration** — Multipart form with ID document upload, duplicate username/email detection
- **Branch & Service Discovery** — Public endpoints to browse branches, services, and available time slots
- **Appointment Booking** — Book, view, cancel, and reschedule appointments with slot capacity enforcement
- **Attachment Download** — Retrieve appointment-related attachments

### Staff & Manager Operations
- **Slot Management** — Create, bulk create, update, and soft-delete time slots with overlap validation
- **Staff Listing** — View staff members with service type assignments
- **Customer Viewing** — List and view customer details; Branch Managers scoped to their branch

### Admin Operations
- **Audit Log Viewing & CSV Export** — Filter by date range, Branch Managers see only their branch logs
- **Soft-Delete Cleanup** — Configurable retention period, bulk purge of expired soft-deleted slots (skips slots with appointments)
- **System Configuration** — Manage retention period settings
- **ID Document Download** — Admin-only access with audit trail
