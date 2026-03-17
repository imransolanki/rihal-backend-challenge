# API Documentation

See [openapi.yaml](./openapi.yaml) for the full OpenAPI 3.0 specification.

## Base URL

```
http://localhost:8080
```

## Authentication

HTTP Basic Auth. Include `Authorization: Basic <base64(username:password)>` header.

Public endpoints (no auth required):
- `GET /api/health`
- `GET /api/public/branches`
- `GET /api/public/branches/{branchId}/services`
- `GET /api/public/slots`
- `POST /api/auth/register`

## Endpoints Summary

### Public
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Health check |
| GET | `/api/public/branches` | List branches |
| GET | `/api/public/branches/{branchId}/services` | Branch services |
| GET | `/api/public/slots?branchId=&serviceTypeId=&date=` | Available slots |
| POST | `/api/auth/register` | Customer registration (multipart) |

### Customer (CUSTOMER role)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/appointments/book` | Book appointment |
| GET | `/api/appointments/my` | My appointments |
| GET | `/api/appointments/{id}` | Appointment detail |
| GET | `/api/appointments/{id}/attachment` | Download attachment |
| POST | `/api/appointments/{id}/cancel` | Cancel appointment |
| POST | `/api/appointments/{id}/reschedule` | Reschedule appointment |

### Staff Management (ADMIN, BRANCH_MANAGER)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/slots` | Create slot |
| POST | `/api/slots/bulk` | Bulk create slots |
| GET | `/api/slots` | List managed slots |
| GET | `/api/slots/{slotId}` | Slot detail |
| PATCH | `/api/slots/{slotId}` | Update slot |
| DELETE | `/api/slots/{slotId}` | Soft-delete slot |
| GET | `/api/staff` | List staff |
| GET | `/api/staff/{staffId}` | Staff detail |
| GET | `/api/customers` | List customers |
| GET | `/api/customers/{customerId}` | Customer detail |
| GET | `/api/audit-logs` | View audit logs |

### Admin Only (ADMIN role)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/customers/{id}/id-document` | Download ID document |
| GET | `/api/admin/config/retention-period` | Get retention config |
| PUT | `/api/admin/config/retention-period` | Update retention config |
| POST | `/api/admin/slots/cleanup` | Execute soft-delete cleanup |
| GET | `/api/admin/audit-logs/export` | Export audit logs as CSV |
