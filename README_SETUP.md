# FlowCare Backend - Setup Instructions

## Prerequisites
- JDK 17 or higher
- Docker and Docker Compose (for PostgreSQL)
- Gradle 8.x (or use included wrapper)

## Database Setup

1. Start PostgreSQL using Docker Compose:
   ```bash
   docker-compose up -d
   ```

2. Verify PostgreSQL is running:
   ```bash
   docker ps
   ```

## Application Setup

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

4. Run the application (migrations run automatically):
   ```bash
   ./gradlew bootRun
   ```

The application will start on `http://localhost:8080`

## Verify Setup

Test the health endpoint:
```bash
curl http://localhost:8080/api/health
```

Expected response:
```json
{
  "status": "UP",
  "timestamp": "2026-03-15T10:00:00"
}
```

Test the protected endpoint (should return 401):
```bash
curl http://localhost:8080/api/test/protected
```

Test with authentication (after seeding in Story 2):
```bash
curl -u admin:Admin@123 http://localhost:8080/api/test/protected
```

## Database Seeding

The application automatically seeds the database on startup with data from `example.json`:
- 2 branches (Muscat, Suhar)
- 6 service types (3 per branch)
- 10 users (1 admin, 2 managers, 4 staff, 3 customers)
- 6 staff-service assignments
- 14 slots
- 2 appointments
- 2 audit log entries

### Seed Credentials

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

### Verify Seeding

```bash
curl -u admin:Admin@123 http://localhost:8080/api/test/protected
```

### Idempotency

Seeding uses PostgreSQL `INSERT ... ON CONFLICT` — running the app multiple times won't create duplicates.

### Disable Seeding

```bash
SEED_ENABLED=false ./gradlew bootRun
```

Or in `application.yml`:
```yaml
seed:
  enabled: false
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| DB_URL | PostgreSQL JDBC URL | jdbc:postgresql://localhost:5432/flowcare_db |
| DB_USERNAME | Database username | flowcare_user |
| DB_PASSWORD | Database password | flowcare_pass |
| SPRING_PROFILES_ACTIVE | Active Spring profile | dev |

## Running Tests

```bash
./gradlew test
```

## Database Migrations

Migrations are in `src/main/resources/db/migration/` and run automatically on startup.

## Troubleshooting

- **Database connection failed**: Ensure PostgreSQL is running (`docker ps`) and check credentials in `application-dev.yml`
- **Port 8080 in use**: Add `server.port: 8081` to `application.yml`
- **Flyway migration failed**: Run `./gradlew clean build` and check migration SQL syntax
