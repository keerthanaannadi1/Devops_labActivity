# Employee Leave & Attendance Management System — Architecture

---

## 1. System Overview

A full-stack web application with three user roles (Employee, Manager, HR/Admin), backed by a Spring Boot REST API, React frontend, and MySQL database. The application is built and deployed through an automated DevOps pipeline.

---

## 2. User Roles & Responsibilities

| Role       | Key Responsibilities |
|------------|----------------------|
| Employee   | Register/login, mark attendance, apply for leave, view leave balance, cancel pending leave |
| Manager    | View team attendance, approve/reject leave requests, view team leave history |
| HR / Admin | Manage employees & departments, configure leave types & balances, view reports |

---

## 3. Application Screens (12 Screens)

```
                    LOGIN
                      │
          ┌───────────┴───────────┐
          │                       │
      EMPLOYEE                 ADMIN/HR
          │                       │
     Dashboard               Dashboard
          │                       │
    ┌─────┼─────┐          ┌──────┼──────┐
    │     │     │          │      │      │
Attendance Leave Profile Employees Leave Reports
          │
    Apply Leave
          │
          ▼
    Manager Approval
          │
      ┌───┴────┐
      ▼        ▼
   APPROVED  REJECTED
```

### Screen List

1. Login (shared)
2. Employee Dashboard
3. Mark Attendance / Attendance History
4. Apply for Leave
5. My Leave Requests
6. Employee Profile
7. Manager Dashboard
8. Manager — Pending Approvals
9. Admin Dashboard
10. Employee Management (Add / Edit / Delete)
11. Department & Leave Type Configuration
12. Reports & Analytics

---

## 4. Backend Architecture

```
React Frontend
       │
       │ REST API (JSON over HTTP)
       ▼
Spring Boot Backend
       │
 ┌─────┼──────────────────┐
 │     │                  │
Auth  Leave           Attendance
 │     │                  │
 └─────┼──────────────────┘
       │
  ┌────┼────────────┐
  │    │            │
Dept  Notif      Reports
  │    │            │
  └────┼────────────┘
       │
       ▼
     MySQL
```

### Backend Modules

| Module         | Responsibility |
|----------------|----------------|
| `auth`         | JWT-based login, role assignment, token refresh |
| `employee`     | Employee CRUD, profile management |
| `department`   | Department CRUD, manager assignment |
| `attendance`   | Mark attendance, fetch history, calculate present days |
| `leave`        | Apply, approve, reject, cancel leave requests; on approval auto-creates attendance records with status ON_LEAVE for each date in range |
| `leave-balance`| Track and update leave quotas per employee per type |
| `notification` | In-app notifications for approval/rejection events |
| `admin`        | Admin-only operations, user role management |
| `reports`      | Attendance summaries, leave analytics, exports |

> **Note:** Authentication must be implemented first — all other modules depend on role-based access control.

---

## 5. Database Schema

```
users
  ├── id (PK)
  ├── email
  ├── password_hash
  ├── role (EMPLOYEE | MANAGER | HR_ADMIN)
  └── created_at

refresh_tokens
  ├── id (PK)
  ├── user_id (FK → users.id)
  ├── token_hash
  ├── expires_at
  ├── revoked (boolean)
  └── created_at

departments
  ├── id (PK)
  ├── name
  └── manager_id (FK → employees.id)

employees
  ├── id (PK)
  ├── user_id (FK → users.id)
  ├── name
  ├── department_id (FK → departments.id)
  ├── manager_id (FK → employees.id)    ← who approves their leave
  ├── designation
  ├── date_of_joining
  └── phone

attendance
  ├── id (PK)
  ├── employee_id (FK → employees.id)
  ├── date
  ├── check_in_time
  ├── check_out_time
  └── status (PRESENT | ABSENT | HALF_DAY | ON_LEAVE)

leave_types
  ├── id (PK)
  ├── name (Casual | Sick | Earned | Comp-off)
  ├── max_days_per_year
  └── carry_forward (boolean)

leave_balances
  ├── id (PK)
  ├── employee_id (FK → employees.id)
  ├── leave_type_id (FK → leave_types.id)
  ├── total_days
  └── used_days

leave_requests
  ├── id (PK)
  ├── employee_id (FK → employees.id)
  ├── leave_type_id (FK → leave_types.id)
  ├── from_date
  ├── to_date
  ├── reason
  ├── status (PENDING | APPROVED | REJECTED | CANCELLED)
  ├── reviewed_by (FK → users.id)   ← service layer must verify reviewer role is MANAGER or HR_ADMIN
  └── reviewed_at

notifications
  ├── id (PK)
  ├── employee_id (FK → employees.id)
  ├── message
  ├── is_read (boolean)
  └── created_at
```

### Entity Relationships

```
departments ──< employees >── leave_balances
                   │
        ┌──────────┼──────────┐
        │          │          │
   attendance  leave_requests notifications
```

---

## 6. REST API Structure

| Module      | Endpoint Prefix         | Example Operations |
|-------------|-------------------------|--------------------|
| Auth        | `/api/auth`             | POST /login, POST /register, POST /refresh |
| Employee    | `/api/employees`        | GET, POST, PUT, DELETE |
| Department  | `/api/departments`      | GET, POST, PUT, DELETE |
| Attendance  | `/api/attendance`       | POST /mark, GET /history, GET /team |
| Leave       | `/api/leaves`           | POST /apply, PUT /approve, PUT /reject, PUT /cancel |
| Balance     | `/api/leave-balances`   | GET /{employeeId} |
| Notification| `/api/notifications`    | GET /mine, PUT /mark-read |
| Reports     | `/api/reports`          | GET /attendance, GET /leaves, GET /department |
| Admin       | `/api/admin`            | User management, config |

---

## 7. DevOps Pipeline

```
YOUR APPLICATION CODE
        │
        ▼
       Git  (local version control)
        │
        ▼
     GitLab  (remote repository)
        │
   Pull Request / Merge Request
        │
        ▼
     Jenkins  (CI/CD trigger on merge)
        │
   ┌────┼────────────┐
   ▼    ▼            ▼
 Build  JUnit      SonarQube
(Maven) Tests    (code quality)
   │    │            │
   └────┼────────────┘
        │
        ▼
     Docker  (containerize app)
        │
        ▼
     Ansible  (provision & deploy to server)
        │
        ▼
   Nginx (reverse proxy)
        │
        ▼
  Application Server
  ┌─────────────────────────────────┐
  │  spring-boot-app  (container)   │  ← listens on :8080
  │  react-app        (container)   │  ← static build served on :3000
  │  mysql-db         (container)   │  ← listens on :3306
  │  nginx            (container)   │  ← :80/:443 → routes /api → :8080, / → :3000
  └─────────────────────────────────┘

> **Serving strategy:** React is built into its own container (Node build → Nginx serve).
> A single top-level Nginx container acts as reverse proxy.
> `/api/*` routes to Spring Boot; everything else routes to the React container.
> This is reflected in `docker-compose.yml` with four named services.
```

### Pipeline Stages

| Stage       | Tool       | Purpose |
|-------------|------------|---------|
| Source      | Git/GitLab | Version control, branch strategy, MRs |
| Build       | Maven      | Compile Spring Boot, run unit tests |
| Migrate     | Flyway     | Apply DB migrations before tests run |
| Test        | JUnit + Karate | Unit tests (backend) + API contract tests written alongside each API |
| Code Quality| SonarQube  | Static analysis, coverage thresholds |
| Package     | Docker     | Build image for backend + frontend |
| Deploy      | Ansible    | Push containers to application server |
| Serve       | Nginx      | Reverse proxy, route `/api` → Spring Boot, `/` → React static build |

---

## 8. Project Folder Structure

```
employee-leave-management/
│
├── backend/                        # Spring Boot project
│   ├── src/
│   │   ├── main/java/com/elms/
│   │   │   ├── auth/
│   │   │   ├── employee/
│   │   │   ├── department/
│   │   │   ├── attendance/
│   │   │   ├── leave/
│   │   │   ├── notification/
│   │   │   ├── reports/
│   │   │   └── admin/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/       # Flyway migrations
│   ├── src/test/                   # JUnit + Karate tests
│   ├── Dockerfile
│   └── pom.xml
│
├── frontend/                       # React project
│   ├── src/
│   │   ├── pages/
│   │   │   ├── Login/
│   │   │   ├── Employee/
│   │   │   ├── Manager/
│   │   │   └── Admin/
│   │   ├── components/
│   │   ├── services/               # Axios API calls
│   │   ├── context/                # Auth context / role guard
│   │   └── App.jsx
│   ├── Dockerfile
│   └── package.json
│
├── devops/
│   ├── Jenkinsfile
│   ├── ansible/
│   │   ├── inventory.ini
│   │   └── deploy.yml
│   ├── docker-compose.yml
│   └── nginx/
│       └── nginx.conf
│
└── ARCHITECTURE.md
```

---

## 9. Build Phases

| Phase | Focus |
|-------|-------|
| 1 | UI/UX wireframes and screen specifications |
| 2 | Database design and Flyway migrations |
| 3 | Spring Boot backend + REST APIs + Karate API contract tests (written alongside each endpoint) |
| 4 | React frontend |
| 5 | Authentication and role-based access (JWT + refresh token table) |
| 6 | Leave & attendance workflow — including auto-sync of attendance records on leave approval |
| 7 | Git branching strategy + GitLab setup |
| 8 | Jenkins CI/CD pipeline (includes Flyway migrate stage) |
| 9 | JUnit unit tests (fill coverage gaps) |
| 10 | Docker containerization (4 containers) + Ansible deployment |

## 10. Key Design Decisions & Rules

| Decision | Rule |
|----------|------|
| JWT refresh tokens | Stored in `refresh_tokens` table with expiry and revocation flag. Never stored client-side in localStorage — use HttpOnly cookies. |
| Leave approval role guard | `reviewed_by` records the approver's `user_id`. Service layer checks `users.role IN (MANAGER, HR_ADMIN)` before allowing approve/reject. |
| Attendance ↔ Leave sync | On leave approval, the `leave` service iterates each date in `[from_date, to_date]` and upserts an `attendance` record with `status = ON_LEAVE`. |
| Karate tests timing | Written in Phase 3 alongside each endpoint, not after. Each module's Karate feature file lives in `src/test/` next to its JUnit tests. |
| Container architecture | Four containers: `nginx`, `spring-boot`, `react`, `mysql`. Nginx is the single entry point. React is a static build served by its own Nginx instance inside the react container. |
| Flyway migrations | All schema changes go through Flyway scripts in `db/migration/`. Jenkins runs `flyway migrate` before tests in the pipeline. |
