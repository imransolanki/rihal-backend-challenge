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

## Implemented Stories

| # | Story | Status |
|---|-------|--------|
| 1 | Project Setup | ✅ |
| 2 | Seed Data | ✅ |
| 3 | Customer Registration | ✅ |
| 4 | Authentication & Authorization | ✅ |
| 5 | Public Branch & Service Discovery | ✅ |
| 6 | Public Slot Viewing | ✅ |
| 7 | Appointment Booking | ✅ |
| 8 | View My Appointments | ✅ |
| 9 | Cancel Appointment | ✅ |
| 10 | Reschedule Appointment | ✅ |
| 11 | Create Slot | ✅ |
| 12 | Bulk Create Slots | ✅ |
| 13 | Update/Delete Slot | ✅ |
| 14-16 | Staff Appointment Management | ✅ |
| 17 | Customer Information Viewing | ✅ |
| 18 | Soft-Delete Cleanup | ✅ |
| 19 | Audit Log Viewing & Export | ✅ |
| 20 | Staff Listing | ✅ |
