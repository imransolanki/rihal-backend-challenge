# FlowCare Queue & Appointment Booking System - User Stories

## Story Organization
Stories are organized by implementation dependency, starting with foundational capabilities and building up to advanced features.

---

## Day-0 Technical Story

### Story 1: Set up minimal project structure with database and API framework

**Story Type:** Technical Story

**Business Context:**
Before any features can be developed, the development team needs a working foundation with a database, API framework, and basic project structure to build upon.

**Story Text:**
As a **Developer**
I want to **set up a minimal project structure with PostgreSQL database, API framework, and basic authentication mechanism**
So that **the team can start building features on a solid foundation**

**Acceptance Criteria:**

1. **Given** the project repository is initialized
   **When** the application starts
   **Then** it should successfully connect to a PostgreSQL database

2. **Given** the database connection is established
   **When** the application starts
   **Then** all required database tables should be created via migration scripts

3. **Given** the API framework is set up
   **When** a request is made to a health check endpoint
   **Then** the API should respond with a success status

4. **Given** basic authentication is configured
   **When** a request is made to a protected endpoint without credentials
   **Then** the system should return an unauthorized error

5. **Given** basic authentication is configured
   **When** a request is made with valid credentials
   **Then** the system should allow access to the protected endpoint

6. **Given** the project structure is set up
   **When** reviewing the codebase
   **Then** it should include:
   - Database migration scripts
   - Environment configuration support
   - Basic error handling
   - README with setup instructions

**Out of Scope:**
- Complex authentication mechanisms (OAuth, JWT)
- Production deployment configuration
- Performance optimization
- Comprehensive logging framework
- API documentation tools

**Dependencies:**
- None (this is the foundational story)

**Assumptions:**
- PostgreSQL is the chosen database
- Basic Authentication is sufficient for the initial implementation
- The team has access to a local or development PostgreSQL instance
- Git repository is already initialized

---

## Foundation Stories (Data & Access)

### Story 2: Seed system with initial data

**Story Type:** Functional Story

**Business Context:**
FlowCare operates multiple branches across Oman with various service types. For the system to be usable, it needs to be populated with branches, service types, staff members, managers, and available appointment slots. The seeding process must be reliable and repeatable across different environments.

**Story Text:**
As a **System Administrator**
I want to **automatically populate the system with branches, service types, staff, managers, and appointment slots from a seed file**
So that **the system is ready for use without manual data entry and can be consistently set up across environments**

**Acceptance Criteria:**

1. **Given** a valid seed JSON file exists
   **When** the application starts for the first time
   **Then** the system should import all branches, service types, staff, managers, and slots from the file

2. **Given** the seed data has been imported once
   **When** the application restarts
   **Then** the system should not create duplicate records (idempotent seeding)

3. **Given** the seed file contains at least 2 branches
   **When** seeding completes
   **Then** all branches should be available in the system

4. **Given** the seed file contains at least 3 service types per branch
   **When** seeding completes
   **Then** all service types should be associated with their respective branches

5. **Given** the seed file contains at least 2 staff members per branch
   **When** seeding completes
   **Then** all staff members should be created with correct branch assignments

6. **Given** the seed file contains at least 1 manager per branch
   **When** seeding completes
   **Then** all managers should be created with correct branch assignments and manager role

7. **Given** the seed file contains slots for the next 3-7 days
   **When** seeding completes
   **Then** at least 10 slots should be available across all branches

8. **Given** slots are being seeded
   **When** each slot is created
   **Then** it should be tied to a specific branch and service type

9. **Given** slots are being seeded
   **When** a slot specifies a staff member
   **Then** the slot should be assigned to that staff member

10. **Given** the system is being seeded
    **When** a default Admin user is created
    **Then** the Admin should have system-wide access permissions

11. **Given** seeding is in progress
    **When** an error occurs during import
    **Then** the system should log the error and provide clear feedback about what failed

**Out of Scope:**
- UI for managing seed data
- Validation of business rules during seeding (e.g., slot conflicts)
- Updating existing records if seed data changes
- Seed data versioning

**Dependencies:**
- Story 1: Project structure and database must be set up

**Assumptions:**
- Seed file format is JSON
- Seed file is provided and follows a defined schema
- Seeding happens at application startup
- Idempotency is achieved by checking for existing records before insertion

**Audit Logging:**
- Seeding actions do not need to be logged in the audit log (system initialization activity)

---

### Story 3: Register as a customer with ID verification

**Story Type:** Functional Story

**Business Context:**
FlowCare needs to verify customer identity before allowing them to book appointments. This ensures accountability and helps prevent fraudulent bookings. Customers must provide valid identification during registration.

**Story Text:**
As a **Customer**
I want to **register for an account by providing my details and uploading my ID document**
So that **I can book appointments at FlowCare branches**

**Acceptance Criteria:**

1. **Given** I am a new customer
   **When** I submit registration with my name, email, phone number, and password
   **Then** my account should be created in the system

2. **Given** I am registering
   **When** I upload my ID document image
   **Then** the system should validate that the file is a valid image format (JPEG, PNG, etc.)

3. **Given** I am uploading an ID document
   **When** the file size exceeds 5 MB
   **Then** the system should reject the upload and inform me of the size limit

4. **Given** I am uploading an ID document
   **When** the file is not a valid image format
   **Then** the system should reject the upload and inform me of acceptable formats

5. **Given** my ID document is valid
   **When** registration is successful
   **Then** the system should store a reference to my ID document in the database

6. **Given** I am registering
   **When** I provide an email that already exists in the system
   **Then** the system should reject the registration and inform me that the email is already in use

7. **Given** I am registering
   **When** I provide incomplete information (missing required fields)
   **Then** the system should reject the registration and inform me which fields are required

8. **Given** my registration is successful
   **When** I try to log in with my credentials
   **Then** I should be able to authenticate successfully

9. **Given** my ID document is uploaded
   **When** the file is stored
   **Then** it should be stored securely (filesystem or object storage)

**Out of Scope:**
- Email verification workflow
- Phone number verification (OTP)
- ID document validation (checking if ID is authentic)
- Password strength requirements beyond basic validation
- Social login (Google, Facebook, etc.)

**Dependencies:**
- Story 1: Authentication mechanism must be in place

**Assumptions:**
- Customer role is automatically assigned upon registration
- ID document is required (not optional)
- File storage mechanism (local filesystem or MinIO) is configured
- Maximum file size is 5 MB
- Accepted image formats: JPEG, PNG, GIF, BMP

**Audit Logging:**
- Customer registration does not need to be logged in the audit log (standard user creation activity)

---

### Story 4: Authenticate and access the system

**Story Type:** Functional Story

**Business Context:**
FlowCare has multiple user types (Admin, Branch Manager, Staff, Customer) with different access levels. The system must authenticate users and enforce role-based access control to ensure users can only perform actions appropriate to their role.

**Story Text:**
As a **User (Admin, Branch Manager, Staff, or Customer)**
I want to **log in with my credentials and access features appropriate to my role**
So that **I can perform my job functions securely within the system**

**Acceptance Criteria:**

1. **Given** I am a registered user
   **When** I provide valid credentials using Basic Authentication
   **Then** I should be successfully authenticated

2. **Given** I am a registered user
   **When** I provide invalid credentials
   **Then** the system should reject my login attempt and return an unauthorized error

3. **Given** I am authenticated as an Admin
   **When** I access system-wide features
   **Then** I should have access to all branches, appointments, staff, and customers

4. **Given** I am authenticated as a Branch Manager
   **When** I access branch-specific features
   **Then** I should only have access to data and operations for my assigned branch

5. **Given** I am authenticated as a Branch Manager
   **When** I attempt to access another branch's data
   **Then** the system should deny access and return a forbidden error

6. **Given** I am authenticated as Staff
   **When** I access appointment features
   **Then** I should only see appointments assigned to me

7. **Given** I am authenticated as Staff
   **When** I attempt to create or delete slots
   **Then** the system should deny access and return a forbidden error

8. **Given** I am authenticated as a Customer
   **When** I access booking features
   **Then** I should only see and manage my own appointments

9. **Given** I am authenticated as a Customer
   **When** I attempt to access administrative features
   **Then** the system should deny access and return a forbidden error

10. **Given** I am not authenticated
    **When** I attempt to access a protected endpoint
    **Then** the system should return an unauthorized error

11. **Given** public endpoints exist (list branches, services, slots)
    **When** I access them without authentication
    **Then** I should be able to view the information

**Out of Scope:**
- Token-based authentication (JWT)
- Session management
- Multi-factor authentication
- Password reset functionality
- Account lockout after failed attempts

**Dependencies:**
- Story 1: Basic authentication mechanism must be implemented
- Story 2: Users (Admin, Managers, Staff) must be seeded

**Assumptions:**
- Basic Authentication is used (username/password in request headers)
- Role is determined from the user record in the database
- Branch assignment for Managers and Staff is stored in the database
- Authorization checks happen on every protected endpoint

**Audit Logging:**
- Login attempts do not need to be logged in the audit log (standard authentication activity)

---
## Public Discovery Stories

### Story 5: Browse available branches and services

**Story Type:** Functional Story

**Business Context:**
Potential customers need to discover what services FlowCare offers and at which branches before deciding to register and book an appointment. This information should be publicly accessible to encourage customer engagement.

**Story Text:**
As a **Potential Customer**
I want to **view all FlowCare branches and the services offered at each branch**
So that **I can decide which branch and service I need before registering**

**Acceptance Criteria:**

1. **Given** I am browsing the system without authentication
   **When** I request a list of all branches
   **Then** I should see all active branches with their names, locations, and contact information

2. **Given** I am viewing the list of branches
   **When** I select a specific branch
   **Then** I should see all service types available at that branch

3. **Given** I am requesting services by branch
   **When** the branch has multiple service types
   **Then** each service should display its name, description, and estimated duration

4. **Given** I am requesting services
   **When** a branch has no services configured
   **Then** the system should return an empty list with an appropriate message

5. **Given** I am browsing branches
   **When** there are no active branches in the system
   **Then** the system should return an empty list with an appropriate message

**Out of Scope:**
- Branch operating hours display
- Service pricing information
- Branch ratings or reviews
- Real-time branch capacity information
- Filtering or searching branches by location

**Dependencies:**
- Story 2: Branches and service types must be seeded

**Assumptions:**
- These endpoints are public (no authentication required)
- All seeded branches are considered "active"
- Service types are associated with specific branches

**Audit Logging:**
- Public browsing does not need to be logged

---

### Story 6: View available appointment slots

**Story Type:** Functional Story

**Business Context:**
Customers need to see what appointment times are available before registering or booking. This transparency helps customers plan their visit and reduces frustration from discovering no slots are available after registration.

**Story Text:**
As a **Potential Customer**
I want to **view available appointment slots for a specific branch and service type**
So that **I can see when appointments are available before registering**

**Acceptance Criteria:**

1. **Given** I am browsing without authentication
   **When** I request available slots for a specific branch and service type
   **Then** I should see all available (unbooked) slots

2. **Given** I am viewing available slots
   **When** slots exist for the selected branch and service
   **Then** each slot should display the date, time, and whether it's assigned to a specific staff member

3. **Given** I am viewing available slots
   **When** I filter by a specific date
   **Then** I should only see slots for that date

4. **Given** I am viewing available slots
   **When** a slot is already booked
   **Then** that slot should not appear in the available slots list

5. **Given** I am viewing available slots
   **When** a slot has been soft-deleted
   **Then** that slot should not appear in the available slots list

6. **Given** I am requesting slots
   **When** no slots are available for the selected criteria
   **Then** the system should return an empty list with an appropriate message

7. **Given** I am viewing available slots
   **When** a slot is assigned to a specific staff member
   **Then** the staff member's name should be displayed with the slot

8. **Given** I am viewing available slots
   **When** a slot is not assigned to any specific staff member
   **Then** the slot should be shown as available without staff assignment

**Out of Scope:**
- Filtering by time range (morning, afternoon, evening)
- Showing partially booked days
- Slot recommendations based on customer preferences
- Real-time slot availability updates

**Dependencies:**
- Story 2: Slots must be seeded
- Story 5: Branches and services must be browsable

**Assumptions:**
- This endpoint is public (no authentication required)
- Date filter is optional
- Slots are shown in chronological order
- Only future slots are shown (past slots are excluded)

**Audit Logging:**
- Public browsing does not need to be logged

---

## Customer Booking Stories

### Story 7: Book an appointment with optional attachment

**Story Type:** Functional Story

**Business Context:**
FlowCare's core business depends on customers being able to book appointments efficiently. Some services require customers to submit supporting documents (medical records, application forms, etc.) at the time of booking to expedite service delivery.

**Story Text:**
As a **Customer**
I want to **book an appointment at a specific branch for a service and optionally upload supporting documents**
So that **I can secure my appointment time and provide any required documentation in advance**

**Acceptance Criteria:**

1. **Given** I am an authenticated customer
   **When** I select an available slot for a branch and service type
   **Then** I should be able to book that slot

2. **Given** I am booking an appointment
   **When** the booking is successful
   **Then** the slot should be marked as booked and no longer available to other customers

3. **Given** I am booking an appointment
   **When** I choose to upload an attachment
   **Then** the system should accept image files (JPEG, PNG) or PDF files

4. **Given** I am uploading an attachment
   **When** the file size exceeds 5 MB
   **Then** the system should reject the upload and inform me of the size limit

5. **Given** I am uploading an attachment
   **When** the file format is not supported
   **Then** the system should reject the upload and inform me of acceptable formats

6. **Given** my attachment is valid
   **When** the booking is successful
   **Then** the system should store the attachment and associate it with my appointment

7. **Given** I am booking an appointment
   **When** I choose not to upload an attachment
   **Then** the booking should still succeed (attachment is optional)

8. **Given** I am attempting to book a slot
   **When** the slot has already been booked by another customer
   **Then** the system should reject my booking and inform me the slot is no longer available

9. **Given** I am attempting to book a slot
   **When** the slot has been soft-deleted
   **Then** the system should reject my booking and inform me the slot is no longer available

10. **Given** my booking is successful
    **When** the appointment is created
    **Then** an audit log entry should be created recording the appointment creation with my user ID and appointment details

11. **Given** my booking is successful
    **When** I view my appointments
    **Then** I should see the newly booked appointment in my list

**Out of Scope:**
- Payment processing
- Appointment confirmation emails/SMS
- Booking on behalf of another person
- Recurring appointments
- Waitlist functionality if no slots available

**Dependencies:**
- Story 3: Customer registration must be complete
- Story 4: Authentication must be working
- Story 6: Available slots must be viewable
- Story 2: Slots must exist in the system

**Assumptions:**
- Each slot can only be booked once
- Attachment is optional
- Maximum file size is 5 MB
- Accepted formats: JPEG, PNG, PDF
- Booking is immediate (no approval workflow)
- Customer can book multiple appointments

**Audit Logging:**
- **Action Type:** APPOINTMENT_CREATED
- **Actor:** Customer user ID and role
- **Target Entity Type:** Appointment
- **Target Entity ID:** Appointment ID
- **Metadata:** Branch ID, Service Type ID, Slot ID, Attachment present (yes/no)

---

### Story 8: View my appointment details and history

**Story Type:** Functional Story

**Business Context:**
Customers need to track their appointments, view details, and access any documents they uploaded. This helps them prepare for their visit and maintain records of their interactions with FlowCare.

**Story Text:**
As a **Customer**
I want to **view all my appointments and see the details of each appointment including any attachments I uploaded**
So that **I can keep track of my scheduled visits and access my documents**

**Acceptance Criteria:**

1. **Given** I am an authenticated customer
   **When** I request my appointment list
   **Then** I should see all my appointments (past and upcoming)

2. **Given** I am viewing my appointments
   **When** the list is displayed
   **Then** each appointment should show the branch name, service type, date, time, and current status

3. **Given** I am viewing my appointments
   **When** I select a specific appointment
   **Then** I should see full details including branch, service, slot time, status, and staff member (if assigned)

4. **Given** I am viewing appointment details
   **When** I uploaded an attachment during booking
   **Then** I should be able to download or view that attachment

5. **Given** I am requesting an attachment
   **When** the attachment is retrieved
   **Then** the system should return it with the correct content-type header

6. **Given** I am requesting an attachment
   **When** the attachment file does not exist
   **Then** the system should return an appropriate error message

7. **Given** I am viewing my appointments
   **When** I have no appointments
   **Then** the system should display an appropriate message indicating no appointments found

8. **Given** I am viewing my appointments
   **When** appointments are displayed
   **Then** they should be sorted by date (upcoming appointments first)

**Out of Scope:**
- Viewing other customers' appointments
- Filtering appointments by status or date range
- Exporting appointment history
- Appointment reminders

**Dependencies:**
- Story 7: Appointments must be bookable
- Story 4: Authentication must be working

**Assumptions:**
- Customers can only view their own appointments
- Past appointments remain visible (not hidden after completion)
- Attachment download requires authentication
- Status values include: booked, checked-in, completed, no-show, cancelled

**Audit Logging:**
- Viewing appointments does not need to be logged (read-only operation)

---

### Story 9: Cancel my appointment

**Story Type:** Functional Story

**Business Context:**
Customers' plans change, and they need the ability to cancel appointments they can no longer attend. Cancelling frees up the slot for other customers and helps FlowCare manage capacity effectively.

**Story Text:**
As a **Customer**
I want to **cancel my upcoming appointment**
So that **I can free up the slot if I can no longer attend and allow others to book it**

**Acceptance Criteria:**

1. **Given** I am an authenticated customer
   **When** I select one of my upcoming appointments
   **Then** I should be able to cancel it

2. **Given** I am cancelling an appointment
   **When** the cancellation is successful
   **Then** the appointment status should be updated to "cancelled"

3. **Given** I am cancelling an appointment
   **When** the cancellation is successful
   **Then** the slot should become available again for other customers to book

4. **Given** I am cancelling an appointment
   **When** the cancellation is successful
   **Then** an audit log entry should be created recording the cancellation with my user ID and appointment details

5. **Given** I am attempting to cancel an appointment
   **When** the appointment has already been cancelled
   **Then** the system should reject the cancellation and inform me it's already cancelled

6. **Given** I am attempting to cancel an appointment
   **When** the appointment has already been completed
   **Then** the system should reject the cancellation and inform me completed appointments cannot be cancelled

7. **Given** I am attempting to cancel an appointment
   **When** the appointment belongs to another customer
   **Then** the system should deny access and return a forbidden error

8. **Given** my cancellation is successful
   **When** I view my appointments
   **Then** the cancelled appointment should show status as "cancelled"

**Out of Scope:**
- Cancellation reasons or feedback
- Cancellation deadlines (e.g., must cancel 24 hours in advance)
- Penalties for late cancellations
- Notification to staff about cancellation

**Dependencies:**
- Story 7: Appointments must be bookable
- Story 8: Customers must be able to view their appointments

**Assumptions:**
- Customers can only cancel their own appointments
- Cancelled appointments remain in the customer's history
- The slot becomes immediately available after cancellation
- No restrictions on how close to the appointment time cancellation can occur

**Audit Logging:**
- **Action Type:** APPOINTMENT_CANCELLED
- **Actor:** Customer user ID and role
- **Target Entity Type:** Appointment
- **Target Entity ID:** Appointment ID
- **Metadata:** Branch ID, Service Type ID, Slot ID, Cancellation timestamp

---

### Story 10: Reschedule my appointment

**Story Type:** Functional Story

**Business Context:**
Customers sometimes need to change their appointment time rather than cancel completely. Rescheduling allows them to maintain their appointment while choosing a more convenient time, improving customer satisfaction and reducing no-shows.

**Story Text:**
As a **Customer**
I want to **reschedule my appointment to a different available slot**
So that **I can change my appointment time without having to cancel and rebook**

**Acceptance Criteria:**

1. **Given** I am an authenticated customer
   **When** I select one of my upcoming appointments
   **Then** I should be able to choose to reschedule it

2. **Given** I am rescheduling an appointment
   **When** I select a new available slot
   **Then** the appointment should be moved to the new slot

3. **Given** I am rescheduling an appointment
   **When** the reschedule is successful
   **Then** the original slot should become available again for other customers

4. **Given** I am rescheduling an appointment
   **When** the reschedule is successful
   **Then** the new slot should be marked as booked

5. **Given** I am rescheduling an appointment
   **When** the reschedule is successful
   **Then** an audit log entry should be created recording the reschedule with old and new slot details

6. **Given** I am attempting to reschedule
   **When** the new slot has already been booked by another customer
   **Then** the system should reject the reschedule and inform me the slot is unavailable

7. **Given** I am attempting to reschedule
   **When** the new slot has been soft-deleted
   **Then** the system should reject the reschedule and inform me the slot is unavailable

8. **Given** I am attempting to reschedule
   **When** the appointment has already been cancelled
   **Then** the system should reject the reschedule and inform me cancelled appointments cannot be rescheduled

9. **Given** I am attempting to reschedule
   **When** the appointment has already been completed
   **Then** the system should reject the reschedule and inform me completed appointments cannot be rescheduled

10. **Given** I am attempting to reschedule
    **When** the appointment belongs to another customer
    **Then** the system should deny access and return a forbidden error

11. **Given** my reschedule is successful
    **When** I view my appointment details
    **Then** the appointment should show the new date and time

12. **Given** I am rescheduling an appointment
    **When** the new slot is for a different service type or branch
    **Then** the appointment should be updated to reflect the new service type and branch

**Out of Scope:**
- Rescheduling limits (e.g., can only reschedule once)
- Rescheduling deadlines (e.g., must reschedule 24 hours in advance)
- Notification to staff about reschedule
- Keeping the same staff member when rescheduling

**Dependencies:**
- Story 7: Appointments must be bookable
- Story 8: Customers must be able to view their appointments
- Story 6: Available slots must be viewable

**Assumptions:**
- Customers can only reschedule their own appointments
- Rescheduling can change branch, service type, date, and time
- Any attachment uploaded with the original booking remains associated with the appointment
- No limit on how many times an appointment can be rescheduled

**Audit Logging:**
- **Action Type:** APPOINTMENT_RESCHEDULED
- **Actor:** Customer user ID and role
- **Target Entity Type:** Appointment
- **Target Entity ID:** Appointment ID
- **Metadata:** Old Slot ID, New Slot ID, Old Branch/Service, New Branch/Service, Reschedule timestamp

---
## Slot Management Stories

### Story 11: Create appointment slots for a branch

**Story Type:** Functional Story

**Business Context:**
FlowCare branches need to control their appointment availability based on staff schedules, operating hours, and service capacity. Admins and Branch Managers must be able to create slots to make appointments available to customers.

**Story Text:**
As an **Admin or Branch Manager**
I want to **create appointment slots for a branch and service type, either individually or in bulk**
So that **customers can book appointments during available times**

**Acceptance Criteria:**

1. **Given** I am authenticated as an Admin
   **When** I create a slot for any branch
   **Then** the slot should be created successfully

2. **Given** I am authenticated as a Branch Manager
   **When** I create a slot for my assigned branch
   **Then** the slot should be created successfully

3. **Given** I am authenticated as a Branch Manager
   **When** I attempt to create a slot for a different branch
   **Then** the system should deny access and return a forbidden error

4. **Given** I am creating a slot
   **When** I specify the branch, service type, date, and time
   **Then** the slot should be created with those details

5. **Given** I am creating a slot
   **When** I optionally assign it to a specific staff member
   **Then** the slot should be created with that staff assignment

6. **Given** I am creating a slot
   **When** I do not assign it to any staff member
   **Then** the slot should be created as unassigned (pool-based)

7. **Given** I am creating slots in bulk
   **When** I provide multiple slot details (e.g., recurring time slots over several days)
   **Then** all valid slots should be created

8. **Given** I am creating slots in bulk
   **When** some slots have validation errors
   **Then** the system should create valid slots and report which slots failed with reasons

9. **Given** I am creating a slot
   **When** the slot is successfully created
   **Then** an audit log entry should be created recording the slot creation

10. **Given** I am creating a slot
    **When** I provide incomplete information (missing required fields)
    **Then** the system should reject the creation and inform me which fields are required

11. **Given** I am authenticated as Staff
    **When** I attempt to create a slot
    **Then** the system should deny access and return a forbidden error

**Out of Scope:**
- Automatic slot generation based on templates
- Validation for overlapping slots for the same staff member
- Slot capacity limits per day
- Integration with staff calendars

**Dependencies:**
- Story 2: Branches and service types must exist
- Story 4: Role-based access control must be working

**Assumptions:**
- Slots can be created for future dates
- Multiple slots can exist for the same time if assigned to different staff or unassigned
- Bulk creation is a single API call with multiple slot objects
- Branch Managers can only create slots for their assigned branch

**Audit Logging:**
- **Action Type:** SLOT_CREATED
- **Actor:** User ID and role (Admin or Branch Manager)
- **Target Entity Type:** Slot
- **Target Entity ID:** Slot ID
- **Metadata:** Branch ID, Service Type ID, Date/Time, Staff ID (if assigned), Bulk creation (yes/no)

---

### Story 12: Update appointment slots

**Story Type:** Functional Story

**Business Context:**
Staff schedules and branch operations change. Admins and Branch Managers need the ability to modify slot details such as time, date, or staff assignment to accommodate these changes while maintaining service availability.

**Story Text:**
As an **Admin or Branch Manager**
I want to **update the details of an existing appointment slot**
So that **I can adjust slot times, dates, or staff assignments when schedules change**

**Acceptance Criteria:**

1. **Given** I am authenticated as an Admin
   **When** I update a slot in any branch
   **Then** the slot should be updated successfully

2. **Given** I am authenticated as a Branch Manager
   **When** I update a slot in my assigned branch
   **Then** the slot should be updated successfully

3. **Given** I am authenticated as a Branch Manager
   **When** I attempt to update a slot in a different branch
   **Then** the system should deny access and return a forbidden error

4. **Given** I am updating a slot
   **When** I change the date or time
   **Then** the slot should reflect the new date/time

5. **Given** I am updating a slot
   **When** I change the staff assignment
   **Then** the slot should be assigned to the new staff member

6. **Given** I am updating a slot
   **When** I remove the staff assignment
   **Then** the slot should become unassigned (pool-based)

7. **Given** I am updating a slot
   **When** I assign it to a staff member
   **Then** the slot should be assigned to that staff member

8. **Given** I am updating a slot
   **When** the slot is already booked
   **Then** the system should allow the update but maintain the booking association

9. **Given** I am updating a slot
   **When** the update is successful
   **Then** an audit log entry should be created recording the slot update with old and new values

10. **Given** I am updating a slot
    **When** the slot has been soft-deleted
    **Then** the system should reject the update and inform me the slot is deleted

11. **Given** I am authenticated as Staff
    **When** I attempt to update a slot
    **Then** the system should deny access and return a forbidden error

**Out of Scope:**
- Notifying customers if their booked slot time changes
- Validation for conflicts with other slots
- Bulk update of multiple slots
- Restricting updates to slots that are already booked

**Dependencies:**
- Story 11: Slots must be creatable
- Story 4: Role-based access control must be working

**Assumptions:**
- Updating a booked slot does not cancel the booking
- Branch Managers can only update slots in their assigned branch
- All slot fields can be updated except the slot ID
- Soft-deleted slots cannot be updated

**Audit Logging:**
- **Action Type:** SLOT_UPDATED
- **Actor:** User ID and role (Admin or Branch Manager)
- **Target Entity Type:** Slot
- **Target Entity ID:** Slot ID
- **Metadata:** Branch ID, Old values (date/time, staff), New values (date/time, staff), Update timestamp

---

### Story 13: Remove appointment slots (soft delete)

**Story Type:** Functional Story

**Business Context:**
FlowCare needs to remove slots that are no longer needed due to staff unavailability, branch closures, or schedule changes. However, for audit and compliance purposes, slots should not be permanently deleted immediately. Instead, they should be soft-deleted and retained for a configurable period.

**Story Text:**
As an **Admin or Branch Manager**
I want to **remove appointment slots by soft-deleting them**
So that **they are no longer available for booking but remain in the system for audit purposes**

**Acceptance Criteria:**

1. **Given** I am authenticated as an Admin
   **When** I delete a slot in any branch
   **Then** the slot should be soft-deleted successfully

2. **Given** I am authenticated as a Branch Manager
   **When** I delete a slot in my assigned branch
   **Then** the slot should be soft-deleted successfully

3. **Given** I am authenticated as a Branch Manager
   **When** I attempt to delete a slot in a different branch
   **Then** the system should deny access and return a forbidden error

4. **Given** I am deleting a slot
   **When** the deletion is successful
   **Then** the slot should have a deleted_at timestamp recorded

5. **Given** I am deleting a slot
   **When** the deletion is successful
   **Then** the slot should no longer appear in normal listing endpoints for customers

6. **Given** I am deleting a slot
   **When** the deletion is successful
   **Then** an audit log entry should be created recording the soft delete

7. **Given** I am an Admin viewing slots
   **When** I request to see deleted slots
   **Then** I should be able to view soft-deleted slots with their deleted_at timestamp

8. **Given** I am deleting a slot
   **When** the slot is already booked
   **Then** the system should reject the deletion and inform me the slot has an active booking

9. **Given** I am deleting a slot
   **When** the slot has already been soft-deleted
   **Then** the system should reject the deletion and inform me the slot is already deleted

10. **Given** I am authenticated as Staff
    **When** I attempt to delete a slot
    **Then** the system should deny access and return a forbidden error

11. **Given** a slot is soft-deleted
    **When** customers browse available slots
    **Then** the soft-deleted slot should not appear in the results

**Out of Scope:**
- Restoring soft-deleted slots
- Bulk deletion of multiple slots
- Deleting slots with past bookings
- Automatic deletion based on date

**Dependencies:**
- Story 11: Slots must be creatable
- Story 4: Role-based access control must be working

**Assumptions:**
- Soft-deleted slots cannot be booked
- Only unbooked slots can be soft-deleted
- Branch Managers can only delete slots in their assigned branch
- Admins can view soft-deleted slots, but Branch Managers and Staff cannot
- Soft-deleted slots will be hard-deleted after a retention period (handled in Story 18)

**Audit Logging:**
- **Action Type:** SLOT_SOFT_DELETED
- **Actor:** User ID and role (Admin or Branch Manager)
- **Target Entity Type:** Slot
- **Target Entity ID:** Slot ID
- **Metadata:** Branch ID, Service Type ID, Date/Time, Staff ID (if assigned), Deleted timestamp

---
## Staff & Appointment Management Stories

### Story 14: Assign staff to services within a branch

**Story Type:** Functional Story

**Business Context:**
FlowCare staff members have different specializations and qualifications. Admins and Branch Managers need to assign staff to specific service types to ensure customers are served by qualified personnel and to enable proper slot assignment.

**Story Text:**
As an **Admin or Branch Manager**
I want to **assign staff members to specific service types within a branch**
So that **staff can be scheduled for services they are qualified to provide**

**Acceptance Criteria:**

1. **Given** I am authenticated as an Admin
   **When** I assign staff to services in any branch
   **Then** the assignment should be created successfully

2. **Given** I am authenticated as a Branch Manager
   **When** I assign staff to services in my assigned branch
   **Then** the assignment should be created successfully

3. **Given** I am authenticated as a Branch Manager
   **When** I attempt to assign staff in a different branch
   **Then** the system should deny access and return a forbidden error

4. **Given** I am assigning staff to a service
   **When** the assignment is successful
   **Then** the staff member should be associated with that service type in the branch

5. **Given** I am assigning staff to a service
   **When** the staff member is already assigned to that service
   **Then** the system should reject the duplicate assignment

6. **Given** I am assigning staff to a service
   **When** the assignment is successful
   **Then** an audit log entry should be created recording the staff assignment

7. **Given** I am viewing staff assignments
   **When** I am an Admin
   **Then** I should see all staff and their service assignments across all branches

8. **Given** I am viewing staff assignments
   **When** I am a Branch Manager
   **Then** I should only see staff and their service assignments in my assigned branch

9. **Given** I am removing a staff assignment
   **When** the removal is successful
   **Then** an audit log entry should be created recording the staff assignment removal

10. **Given** I am authenticated as Staff or Customer
    **When** I attempt to manage staff assignments
    **Then** the system should deny access and return a forbidden error

**Out of Scope:**
- Staff qualification verification
- Staff availability/schedule management
- Staff performance tracking
- Automatic assignment based on workload

**Dependencies:**
- Story 2: Staff and service types must exist
- Story 4: Role-based access control must be working

**Assumptions:**
- A staff member can be assigned to multiple service types
- A service type can have multiple staff members assigned
- Branch Managers can only manage staff in their assigned branch
- Staff assignments are at the branch level (staff belong to one branch)

**Audit Logging:**
- **Action Type:** STAFF_ASSIGNED or STAFF_UNASSIGNED
- **Actor:** User ID and role (Admin or Branch Manager)
- **Target Entity Type:** StaffServiceType
- **Target Entity ID:** Assignment ID
- **Metadata:** Staff ID, Service Type ID, Branch ID, Assignment/Removal timestamp

---

### Story 15: View and manage appointments based on role

**Story Type:** Functional Story

**Business Context:**
Different roles need different views of appointments. Admins need system-wide visibility, Branch Managers need branch-level visibility, and Staff need to see their assigned appointments. This ensures each role can effectively manage their responsibilities.

**Story Text:**
As an **Admin, Branch Manager, or Staff member**
I want to **view appointments relevant to my role and responsibilities**
So that **I can manage and track appointments within my scope of authority**

**Acceptance Criteria:**

1. **Given** I am authenticated as an Admin
   **When** I request the appointments list
   **Then** I should see all appointments across all branches

2. **Given** I am authenticated as a Branch Manager
   **When** I request the appointments list
   **Then** I should only see appointments for my assigned branch

3. **Given** I am authenticated as Staff
   **When** I request the appointments list
   **Then** I should only see appointments assigned to me (via slot assignment)

4. **Given** I am viewing appointments
   **When** the list is displayed
   **Then** each appointment should show customer name, branch, service type, date, time, status, and staff member (if assigned)

5. **Given** I am viewing appointments
   **When** appointments exist
   **Then** they should be sorted by date and time (upcoming appointments first)

6. **Given** I am an Admin viewing appointments
   **When** I filter by branch
   **Then** I should see only appointments for that branch

7. **Given** I am an Admin viewing appointments
   **When** I filter by status
   **Then** I should see only appointments with that status

8. **Given** I am a Branch Manager
   **When** I attempt to view appointments from another branch
   **Then** the system should deny access and return a forbidden error

9. **Given** I am Staff
   **When** I attempt to view appointments not assigned to me
   **Then** the system should deny access and return a forbidden error

10. **Given** I am viewing appointments
    **When** no appointments match my role's scope
    **Then** the system should display an appropriate message indicating no appointments found

**Out of Scope:**
- Advanced filtering (by date range, customer name, service type)
- Exporting appointment lists
- Appointment statistics or analytics
- Calendar view of appointments

**Dependencies:**
- Story 7: Appointments must be bookable
- Story 4: Role-based access control must be working

**Assumptions:**
- Staff see appointments based on slot assignment (if slot is assigned to them)
- Unassigned slots' appointments are visible to Branch Managers and Admins but not to Staff
- Status values include: booked, checked-in, completed, no-show, cancelled
- Branch Managers cannot see appointments from other branches

**Audit Logging:**
- Viewing appointments does not need to be logged (read-only operation)

---

### Story 16: Update appointment status during service delivery

**Story Type:** Functional Story

**Business Context:**
As customers arrive and receive service, FlowCare staff need to track appointment progress through different stages. This helps manage queues, track no-shows, and maintain accurate records of service delivery.

**Story Text:**
As a **Staff member, Branch Manager, or Admin**
I want to **update the status of appointments as customers progress through their visit**
So that **we can track service delivery and maintain accurate appointment records**

**Acceptance Criteria:**

1. **Given** I am authenticated as Staff
   **When** I update the status of an appointment assigned to me
   **Then** the appointment status should be updated successfully

2. **Given** I am authenticated as a Branch Manager
   **When** I update the status of any appointment in my branch
   **Then** the appointment status should be updated successfully

3. **Given** I am authenticated as an Admin
   **When** I update the status of any appointment
   **Then** the appointment status should be updated successfully

4. **Given** I am updating an appointment status
   **When** I change it to "checked-in"
   **Then** the status should be updated and timestamped

5. **Given** I am updating an appointment status
   **When** I change it to "completed"
   **Then** the status should be updated and timestamped

6. **Given** I am updating an appointment status
   **When** I change it to "no-show"
   **Then** the status should be updated and timestamped

7. **Given** I am Staff updating an appointment
   **When** the appointment is not assigned to me
   **Then** the system should deny access and return a forbidden error

8. **Given** I am updating an appointment status
   **When** I optionally add internal notes
   **Then** the notes should be saved with the appointment

9. **Given** I am updating an appointment status
   **When** the appointment has been cancelled
   **Then** the system should reject the status update and inform me the appointment is cancelled

10. **Given** I am authenticated as a Customer
    **When** I attempt to update appointment status
    **Then** the system should deny access and return a forbidden error

11. **Given** I am viewing appointment details
    **When** internal notes exist
    **Then** they should be visible to Staff, Branch Managers, and Admins (but not to Customers)

**Out of Scope:**
- Automatic status transitions (e.g., auto-marking as no-show after time passes)
- Status change notifications to customers
- Service duration tracking
- Staff cannot cancel/reschedule on behalf of customers (unless explicitly allowed by policy)

**Dependencies:**
- Story 7: Appointments must be bookable
- Story 15: Appointments must be viewable by role
- Story 4: Role-based access control must be working

**Assumptions:**
- Valid status values: booked, checked-in, completed, no-show, cancelled
- Staff can only update appointments assigned to them
- Branch Managers can update any appointment in their branch
- Internal notes are optional
- Status updates do not require customer confirmation

**Audit Logging:**
- Status updates do not need to be logged in the audit log (operational activity, not sensitive change)
- However, if internal notes contain sensitive information, consider logging

---
## Administrative & Compliance Stories

### Story 17: View customer information and ID documents

**Story Type:** Functional Story

**Business Context:**
FlowCare needs to verify customer identity and access customer information for service delivery, compliance, and security purposes. Admins require access to customer details including uploaded ID documents, while Branch Managers need to view customers who have appointments at their branch.

**Story Text:**
As an **Admin or Branch Manager**
I want to **view customer information and access their ID documents**
So that **I can verify customer identity and manage customer records**

**Acceptance Criteria:**

1. **Given** I am authenticated as an Admin
   **When** I request the customer list
   **Then** I should see all customers in the system

2. **Given** I am authenticated as a Branch Manager
   **When** I request the customer list
   **Then** I should see customers who have appointments at my branch

3. **Given** I am viewing the customer list
   **When** customers exist
   **Then** each customer should display name, email, phone number, and registration date

4. **Given** I am authenticated as an Admin
   **When** I view a specific customer's details
   **Then** I should see full customer information including ID document reference

5. **Given** I am authenticated as an Admin
   **When** I request to download a customer's ID document
   **Then** the system should return the ID image with correct content-type headers

6. **Given** I am requesting a customer's ID document
   **When** the file does not exist
   **Then** the system should return an appropriate error message

7. **Given** I am authenticated as a Branch Manager
   **When** I attempt to view ID documents
   **Then** the system should deny access (only Admins can view ID documents)

8. **Given** I am authenticated as Staff or Customer
   **When** I attempt to view customer lists or ID documents
   **Then** the system should deny access and return a forbidden error

9. **Given** I am viewing customer details
   **When** the customer has appointments
   **Then** I should see a summary of their appointment history

**Out of Scope:**
- Editing customer information
- Deleting customer accounts
- Customer communication features
- Customer segmentation or analytics
- Downloading multiple ID documents in bulk

**Dependencies:**
- Story 3: Customers must be registered with ID documents
- Story 4: Role-based access control must be working

**Assumptions:**
- Only Admins can view ID documents
- Branch Managers can view customer lists but not ID documents
- ID documents are stored securely (filesystem or object storage)
- Customer passwords are never displayed
- Appointment attachments are separate from ID documents (handled in Story 8)

**Audit Logging:**
- Viewing customer lists does not need to be logged
- Accessing ID documents should be considered for logging if required by compliance policies

---

### Story 18: Configure and execute soft-delete cleanup

**Story Type:** Functional Story

**Business Context:**
FlowCare needs to manage data retention for compliance and storage efficiency. Soft-deleted slots should be retained for a configurable period for audit purposes, then permanently removed. Admins must be able to configure the retention period and execute cleanup operations.

**Story Text:**
As an **Admin**
I want to **configure the retention period for soft-deleted slots and execute cleanup to permanently remove expired records**
So that **we maintain compliance with data retention policies while managing storage efficiently**

**Acceptance Criteria:**

1. **Given** I am authenticated as an Admin
   **When** I configure the soft-delete retention period
   **Then** the system should store the retention period value (in days)

2. **Given** I am authenticated as an Admin
   **When** I view the current retention period
   **Then** the system should display the configured number of days

3. **Given** I am authenticated as an Admin
   **When** I execute the cleanup operation
   **Then** the system should identify all soft-deleted slots where deleted_at is older than the retention period

4. **Given** I am executing cleanup
   **When** soft-deleted slots have passed the retention period
   **Then** those slots should be permanently deleted (hard delete) from the database

5. **Given** I am executing cleanup
   **When** a slot is hard-deleted
   **Then** an audit log entry should be created recording the hard delete action

6. **Given** I am executing cleanup
   **When** a slot is hard-deleted
   **Then** the audit log entry for the original soft-delete should NOT be removed

7. **Given** I am executing cleanup
   **When** a hard-deleted slot has related data (appointments, bookings)
   **Then** the system should either remove that related data or set references to null

8. **Given** I am executing cleanup
   **When** the cleanup operation runs multiple times
   **Then** it should be idempotent (not cause errors or delete more than intended)

9. **Given** I am executing cleanup
   **When** the operation completes
   **Then** the system should return a summary of how many slots were hard-deleted

10. **Given** I am authenticated as Branch Manager, Staff, or Customer
    **When** I attempt to configure retention or execute cleanup
    **Then** the system should deny access and return a forbidden error

11. **Given** I am executing cleanup
    **When** soft-deleted slots have NOT passed the retention period
    **Then** those slots should remain in the database (not hard-deleted)

**Out of Scope:**
- Automatic scheduled cleanup (covered in bonus features)
- Retention policies for other entities (customers, appointments)
- Restoring hard-deleted slots
- Configuring different retention periods for different branches

**Dependencies:**
- Story 13: Slots must be soft-deletable
- Story 4: Role-based access control must be working

**Assumptions:**
- Retention period is a single system-wide value stored in the database
- Default retention period is 30 days (if not configured)
- Cleanup is manually triggered by Admin (not automatic)
- Hard delete removes the slot record but preserves audit logs
- Related appointment records either have slot_id set to null or are also deleted

**Audit Logging:**
- **Action Type:** SLOT_HARD_DELETED
- **Actor:** Admin user ID and role
- **Target Entity Type:** Slot
- **Target Entity ID:** Slot ID (before deletion)
- **Metadata:** Branch ID, Service Type ID, Original deleted_at timestamp, Hard delete timestamp, Retention period used

---

### Story 19: Export audit logs for compliance

**Story Type:** Functional Story

**Business Context:**
FlowCare must maintain audit trails for compliance, security investigations, and operational analysis. Admins need to view all audit logs and export them for reporting, while Branch Managers need visibility into their branch's activities.

**Story Text:**
As an **Admin or Branch Manager**
I want to **view audit logs and export them as a CSV file**
So that **I can track system activities, investigate issues, and meet compliance requirements**

**Acceptance Criteria:**

1. **Given** I am authenticated as an Admin
   **When** I request audit logs
   **Then** I should see all audit log entries across all branches

2. **Given** I am authenticated as a Branch Manager
   **When** I request audit logs
   **Then** I should only see audit log entries for my assigned branch

3. **Given** I am viewing audit logs
   **When** logs are displayed
   **Then** each entry should show action type, actor (user ID and role), target entity type, target entity ID, timestamp, and metadata

4. **Given** I am viewing audit logs
   **When** logs exist
   **Then** they should be sorted by timestamp (most recent first)

5. **Given** I am authenticated as an Admin
   **When** I request to export audit logs
   **Then** the system should generate a CSV file with all audit log entries

6. **Given** I am exporting audit logs
   **When** the CSV is generated
   **Then** it should include columns for: action type, actor user ID, actor role, target entity type, target entity ID, timestamp, and metadata

7. **Given** I am authenticated as a Branch Manager
   **When** I attempt to export all audit logs
   **Then** the system should deny access (only Admins can export all logs)

8. **Given** I am viewing audit logs
   **When** no logs match my role's scope
   **Then** the system should display an appropriate message indicating no logs found

9. **Given** I am authenticated as Staff or Customer
   **When** I attempt to view or export audit logs
   **Then** the system should deny access and return a forbidden error

10. **Given** I am viewing audit logs
    **When** the metadata field contains JSON
    **Then** it should be displayed in a readable format

11. **Given** audit logs are being recorded
    **When** sensitive actions occur (appointment creation, cancellation, reschedule, slot changes, staff assignments, hard deletes)
    **Then** audit log entries should be created automatically

**Out of Scope:**
- Filtering audit logs by date range, action type, or user
- Real-time audit log streaming
- Audit log retention policies
- Editing or deleting audit logs
- Exporting in formats other than CSV

**Dependencies:**
- Story 7, 9, 10: Appointments must generate audit logs
- Story 11, 12, 13: Slot operations must generate audit logs
- Story 14: Staff assignments must generate audit logs
- Story 18: Hard deletes must generate audit logs
- Story 4: Role-based access control must be working

**Assumptions:**
- Audit logs are stored in a dedicated database table
- Audit logs are never deleted (permanent record)
- Branch Managers can only view logs related to their branch
- CSV export includes all fields from the audit log table
- Metadata is stored as JSON and exported as a string in CSV

**Audit Logging:**
- Viewing audit logs does not need to be logged (read-only operation)
- Exporting audit logs could be logged if required by compliance policies

---

### Story 20: List staff members by role

**Story Type:** Functional Story

**Business Context:**
FlowCare needs to manage staff across multiple branches. Admins require visibility into all staff members system-wide for resource planning and management, while Branch Managers need to view staff in their assigned branch for scheduling and operational purposes.

**Story Text:**
As an **Admin or Branch Manager**
I want to **view a list of staff members based on my role's scope**
So that **I can see available staff for scheduling, assignments, and management purposes**

**Acceptance Criteria:**

1. **Given** I am authenticated as an Admin
   **When** I request the staff list
   **Then** I should see all staff members across all branches

2. **Given** I am authenticated as a Branch Manager
   **When** I request the staff list
   **Then** I should only see staff members assigned to my branch

3. **Given** I am viewing the staff list
   **When** staff members exist
   **Then** each staff member should display their name, email, branch assignment, and active status

4. **Given** I am viewing the staff list
   **When** staff members are displayed
   **Then** they should be sorted by branch and then by name

5. **Given** I am authenticated as an Admin
   **When** I view a specific staff member's details
   **Then** I should see full information including their service type assignments

6. **Given** I am authenticated as a Branch Manager
   **When** I attempt to view staff from another branch
   **Then** the system should deny access and return a forbidden error

7. **Given** I am viewing the staff list
   **When** no staff members match my role's scope
   **Then** the system should display an appropriate message indicating no staff found

8. **Given** I am authenticated as Staff or Customer
   **When** I attempt to view the staff list
   **Then** the system should deny access and return a forbidden error

**Out of Scope:**
- Filtering staff by service type or availability
- Staff performance metrics
- Staff schedule/calendar view
- Creating or editing staff records
- Deactivating staff members

**Dependencies:**
- Story 2: Staff must be seeded
- Story 4: Role-based access control must be working

**Assumptions:**
- Staff are assigned to a single branch
- Only active staff are shown by default
- Branch Managers can only view staff in their assigned branch
- Staff details include their service type assignments (from Story 14)

**Audit Logging:**
- Viewing staff lists does not need to be logged (read-only operation)

---

## Summary

This document contains 20 user stories organized by implementation dependency:

**Day-0 (1 story):**
- Story 1: Set up minimal project structure

**Foundation (3 stories):**
- Story 2: Seed system with initial data
- Story 3: Register as a customer with ID verification
- Story 4: Authenticate and access the system

**Public Discovery (2 stories):**
- Story 5: Browse available branches and services
- Story 6: View available appointment slots

**Customer Booking (4 stories):**
- Story 7: Book an appointment with optional attachment
- Story 8: View my appointment details and history
- Story 9: Cancel my appointment
- Story 10: Reschedule my appointment

**Slot Management (3 stories):**
- Story 11: Create appointment slots for a branch
- Story 12: Update appointment slots
- Story 13: Remove appointment slots (soft delete)

**Staff & Appointment Management (3 stories):**
- Story 14: Assign staff to services within a branch
- Story 15: View and manage appointments based on role
- Story 16: Update appointment status during service delivery

**Administrative & Compliance (4 stories):**
- Story 17: View customer information and ID documents
- Story 18: Configure and execute soft-delete cleanup
- Story 19: Export audit logs for compliance
- Story 20: List staff members by role

---

## Notes on Non-Functional Requirements (NFRs)

Several NFRs are embedded within the functional stories as acceptance criteria:

1. **Security & Authorization:** Role-based access control is enforced in Stories 4, 11-19
2. **File Storage:** File upload validation and storage in Stories 3, 7, 8, 17
3. **Data Integrity:** Idempotent operations in Stories 2, 18
4. **Audit & Compliance:** Audit logging in Stories 7, 9, 10, 11, 12, 13, 14, 18, 19
5. **Data Retention:** Soft delete and retention policies in Stories 13, 18

If you require separate NFR stories for performance, scalability, or other quality attributes, please let me know and I can create those as well.
