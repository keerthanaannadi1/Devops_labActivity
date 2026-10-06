# Employee Leave & Attendance Management System — Architecture

> Minimal-stack rewrite. Domain and core workflow preserved from the original design;
> tooling, schema, and modules trimmed to match the lab syllabus. See §11 for what was removed and why.

---

## 1. System Overview

A Spring Boot REST API for employee leave requests and attendance tracking, with role-based
access for three roles (Employee, Manager, HR Admin). The API is containerized with Docker and
deployed to a local Kubernetes cluster. There is no web frontend — Swagger UI serves as the
interactive API client.

---

## 2. User Roles

| Role      | Capabilities |
|-----------|--------------|
| Employee  | Mark attendance, apply for leave, cancel own pending leave, view own leave history and balance |
| Manager   | All Employee capabilities for their own record, plus approve/reject leave for direct reports, view team attendance |
| HR Admin  | All Manager capabilities, plus employee and department CRUD, configure leave types, view reports |

Role is stored as an enum column on `employees` and enforced by Spring Security at the endpoint level.

---

## 3. Backend Modules

| Package     | Responsibility |
|-------------|----------------|
| `employee`  | Employee CRUD, profile |
| `department`| Department CRUD |
| `attendance`| Mark attendance, fetch history, calculate present days |
| `leave`     | Apply / approve / reject / cancel leave; on approval auto-creates `attendance` rows with status `ON_LEAVE` for each date in range |
| `reports`   | Attendance summaries and leave analytics |

Five packages, each a single controller/service/repository trio. No cross-module abstraction layers.

---

## 4. Database Schema

Four tables. Schema is created by `schema.sql` on startup — no migration tool.

```
departments
  ├── id (PK)
  └── name

employees
  ├── id (PK)
  ├── name
  ├── email (unique)
  ├── password_hash                    # BCrypt
  ├── role (EMPLOYEE | MANAGER | HR_ADMIN)
  ├── department_id (FK → departments.id)
  ├── manager_id (FK → employees.id)   # who approves their leave; null for HR Admin
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
  UNIQUE (employee_id, date)

leave_requests
  ├── id (PK)
  ├── employee_id (FK → employees.id)
  ├── leave_type (CASUAL | SICK | EARNED | COMP_OFF)   # enum, not a table
  ├── from_date
  ├── to_date
  ├── reason
  ├── status (PENDING | APPROVED | REJECTED | CANCELLED)
  ├── reviewed_by (FK → employees.id)  # approver's employee id
  └── reviewed_at
```

### Entity Relationships

```
departments ──< employees >── attendance
                    │    └──< leave_requests
              manager_id (self-reference)
```

### Derived Instead of Stored

| Removed table | Replaced by |
|---------------|-------------|
| `users` | `role` + `password_hash` columns on `employees` |
| `leave_types` | Java enum `LeaveType` |
| `leave_balances` | Computed at read time: `sum(days in approved requests)` subtracted from a per-type quota constant |
| `notifications` | Not implemented |
| `refresh_tokens` | Not needed — auth is stateless HTTP Basic |

---

## 5. REST API Structure

| Module      | Prefix               | Operations |
|-------------|----------------------|------------|
| Employee    | `/api/employees`     | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` |
| Department  | `/api/departments`   | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` |
| Attendance  | `/api/attendance`    | `POST /mark`, `GET /history`, `GET /team?date=` |
| Leave       | `/api/leaves`        | `POST /apply`, `GET /mine`, `GET /pending`, `PUT /{id}/approve`, `PUT /{id}/reject`, `PUT /{id}/cancel` |
| Reports     | `/api/reports`       | `GET /attendance`, `GET /leaves`, `GET /department` |

No `/api/auth` prefix — authentication is Spring Security HTTP Basic, so there is no login endpoint.

Interactive docs: Swagger UI at `/swagger-ui.html` (springdoc-openapi).

---

## 6. Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.x |
| Build | Maven | 3.9+ |
| Persistence | Spring Data JPA + Hibernate | managed |
| Database | MySQL | 8.x |
| API Docs | springdoc-openapi | 2.x |
| Security | Spring Security (HTTP Basic + BCrypt) | managed |
| Unit/Integration Testing | spring-boot-starter-test (JUnit 5, Mockito, AssertJ) | managed |
| Slice Testing | `@WebMvcTest`, `@DataJpaTest` | managed |
| Real-DB Testing | Testcontainers (MySQL) | 1.20+ |
| API Testing | Karate (`karate-junit5`) | 1.5.1 |
| Containerization | Docker | 24+ |
| Orchestration | Kubernetes via `kind` | v0.24+ |

**Deliberately absent:** React, Node, Nginx, Jenkins, GitLab CI, SonarQube, Flyway, Ansible, JWT libraries.

---

## 7. Testing Strategy

Three layers, each mapped to a syllabus requirement.

### 7.1 Spring Boot Testing

| Test type | Annotation | What it covers |
|-----------|-----------|----------------|
| Unit | `@ExtendWith(MockitoExtension.class)` | Service logic in isolation — leave approval rules, date-range calculations, role guards |
| MVC slice | `@WebMvcTest(LeaveController.class)` | Controller routing, validation, status codes, JSON shape. Service mocked with `@MockitoBean` |
| JPA slice | `@DataJpaTest` | Repository queries, custom finder methods, unique constraints. Uses in-memory H2 |
| Integration | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Testcontainers` | Full request → controller → service → MySQL path against a real MySQL container |

### 7.2 Karate — API Contract Tests

Gherkin feature files under `src/test/java/features/`, run by `karate-junit5`.

```
src/test/java/
├── features/
│   ├── karate-config.js              # baseUrl, DB cleanup before each scenario
│   ├── employee.feature
│   ├── attendance.feature
│   └── leave.feature
└── com/elms/
    ├── KarateTestRunner.java         # @Suite @SelectClasspathResource("features")
    └── ...Spring Boot tests...
```

Scenarios assert status codes, response schema (`match response == {...}`), and negative cases
(approving own leave, double-marking attendance, cancelling an already-approved request).

### 7.3 Karate — Standalone JavaScript Exercise

A standalone `.feature` file that contains no HTTP calls — pure JS logic — executed directly by the
Karate CLI, independent of the Spring Boot app.

```
karate-js-demo/
├── test.js                # require('@karatelabs/karate'); karate.exec()
├── package.json
└── features/
    └── leave-days.feature # Scenario: count working days in a range
```

```javascript
Feature: Leave day calculations

Scenario: Excluding weekends from a leave range
  * def start = '2026-03-02'
  * def end = '2026-03-09'
  * def days = []
  * def cursor = java.time.LocalDate.parse(start)
  * def last = java.time.LocalDate.parse(end)
  * while (cursor <= last)
    * eval if (!['SATURDAY','SUNDAY'].contains(cursor.dayOfWeek.toString())) days.add(cursor.toString())
    * eval cursor = cursor.plusDays(1)
  * print 'working days =', days.length
  * match days.length == 6
```

---

## 8. Containerization

### 8.1 Backend Dockerfile

Multi-stage build in `backend/Dockerfile`.

```dockerfile
# Stage 1 — build
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2 — runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN adduser -D appuser
COPY --from=build /build/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

Runtime image carries no Maven and no source. Runs as non-root.

### 8.2 Images Produced

| Image | Base | Contents |
|-------|------|----------|
| `elms-api` | `eclipse-temurin:21-jre-alpine` | Fat jar, runs on `:8080` |

That is the only application image. MySQL is not custom-built — the official image is used directly.

---

## 9. Kubernetes Deployment

Local cluster via `kind`. No remote registry required — images are loaded into the cluster's
containerd directly.

```bash
kind create cluster --name elms
docker build -t elms-api:1.0 ./backend
kind load docker-image elms-api:1.0 --name elms
kubectl apply -f k8s/
kubectl rollout status deployment/elms-api
```

### 9.1 Manifests

| File | Contents |
|------|----------|
| `k8s/00-namespace.yaml` | `elms` namespace |
| `k8s/01-configmap.yaml` | `SPRING_PROFILES_ACTIVE`, server port, H2 console flag — non-sensitive settings |
| `k8s/02-secret.yaml` | `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` — base64, never committed in plain text |
| `k8s/10-mysql.yaml` | MySQL `Deployment` + `ClusterIP` Service + `PVC` (1Gi) for data persistence |
| `k8s/20-app-deployment.yaml` | API `Deployment`, **2 replicas**, image `elms-api:1.0`, env from ConfigMap + Secret, `readinessProbe` on `/actuator/health` |
| `k8s/30-app-service.yaml` | API `ClusterIP` Service on `:8080` |

### 9.2 Configuration Flow

```
ConfigMap  ─┐
            ├─> env vars ─> elms-api Deployment
Secret     ─┘                     │
                                  v
                          MySQL Service (mysql:3306)
                                  ^
                                  │
                            MySQL Deployment
                                  │
                                  v
                          PVC (data survives pod restart)
```

No hardcoded URLs or credentials in the image. All external config arrives as environment variables.

### 9.3 Automation Script

`devops/deploy.sh` performs the full run end to end:

```bash
#!/usr/bin/env bash
set -euo pipefail
CLUSTER=elms

kind get clusters | grep -q "$CLUSTER" || kind create cluster --name "$CLUSTER"
mvn -B clean package                                # build + run Spring Boot tests
docker build -t elms-api:1.0 ./backend
kind load docker-image elms-api:1.0 --name "$CLUSTER"
kubectl apply -f k8s/
kubectl -n elms rollout status deployment/elms-api --timeout=180s
kubectl -n elms get all
```

### 9.4 Verifying the Deployment

| Check | Command |
|-------|---------|
| Pods running | `kubectl -n elms get pods` |
| Scaling | `kubectl -n elms scale deployment/elms-api --replicas=3` |
| Logs | `kubectl -n elms logs -l app=elms-api -f` |
| API access | `kubectl -n elms port-forward svc/elms-api 8080:8080` → `http://localhost:8080/swagger-ui.html` |
| Data persisted | `kubectl -n elms exec deploy/mysql -- mysql -uapp -papp elms -e "SELECT COUNT(*) FROM employees;"` |
| Karate against live cluster | `karate -DbaseUrl=http://localhost:8080 features/` |

---

## 10. Project Folder Structure

```
employee-leave-management/
├── backend/
│   ├── src/main/java/com/elms/
│   │   ├── employee/
│   │   ├── department/
│   │   ├── attendance/
│   │   ├── leave/
│   │   ├── reports/
│   │   ├── config/              # SecurityConfig, OpenApiConfig
│   │   └── common/              # exception handling, ApiError
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── schema.sql
│   ├── src/test/java/
│   │   ├── com/elms/            # @WebMvcTest, @DataJpaTest, @SpringBootTest, unit tests
│   │   └── features/            # Karate feature files + karate-config.js
│   ├── Dockerfile
│   └── pom.xml
│
├── karate-js-demo/              # standalone JS + Karate exercise (no Spring Boot)
│   ├── test.js
│   ├── package.json
│   └── features/leave-days.feature
│
├── k8s/
│   ├── 00-namespace.yaml
│   ├── 01-configmap.yaml
│   ├── 02-secret.yaml
│   ├── 10-mysql.yaml
│   ├── 20-app-deployment.yaml
│   └── 30-app-service.yaml
│
├── devops/
│   └── deploy.sh
│
└── ARCHITECTURE.md
```

---

## 11. Build Phases

Each phase maps to one deliverable and one or more syllabus requirements.

| Phase | Deliverable | Syllabus coverage |
|-------|-------------|-------------------|
| 1 | Project scaffold, `pom.xml`, 4-table `schema.sql`, seed data | — |
| 2 | Entities, repositories, services | — |
| 3 | REST controllers + Spring Security role rules + Swagger UI | — |
| 4 | Spring Boot tests: unit + `@WebMvcTest` + `@DataJpaTest` + Testcontainers integration | **Item 5** |
| 5 | Install Karate, explore standalone JAR + CLI, run `httpbin.feature` | **Item 3** |
| 6 | Karate API contract tests for the containerized app + standalone JS demo | **Items 4, 5** |
| 7 | Multi-stage `Dockerfile`, build and run image locally | **Item 1** |
| 8 | `kind` cluster, all six manifests, `kubectl apply`, verify pods and endpoint | **Items 1, 2** |
| 9 | `deploy.sh` end-to-end automation + scaling demo + Karate run against live cluster | **Item 2** |

---

## 12. Key Design Decisions

| Decision | Rule |
|----------|------|
| No frontend | Swagger UI is the API client. No React, Node, or Nginx in the stack |
| Auth | Spring Security HTTP Basic + BCrypt. No JWT, no refresh-token table, no login endpoint |
| Roles | Enum column on `employees`. Method-level security (`@PreAuthorize`) on controllers, not in the service layer |
| Leave approval guard | `reviewed_by` records the approver's `employees.id`. The approver must be the requester's `manager_id` or hold `HR_ADMIN`; otherwise 403 |
| Attendance ↔ leave sync | On approval, iterate every date in `[from_date, to_date]` and upsert an `attendance` row with `status = ON_LEAVE`. `UNIQUE (employee_id, date)` makes the upsert idempotent |
| Unique attendance per day | `UNIQUE (employee_id, date)` — re-marking the same day updates rather than duplicates |
| Leave type | Java enum, not a table. HR Admin configures quotas as constants in `application.yml`, not rows |
| Schema management | `schema.sql` executed on startup. No migration tool — acceptable because there is no production data to preserve |
| Leave balance | Computed on read from approved requests. No `leave_balances` table |
| Karate test data | `karate-config.js` calls `DELETE` endpoints before each scenario instead of truncating tables — keeps tests independent of schema internals |
| Layered tests | Unit tests for logic, `@WebMvcTest` for controllers, Testcontainers for the real DB. `@SpringBootTest` reserved for a small number of end-to-end flows |
| Container build | Multi-stage; runtime image is JRE-only and runs as non-root |
| Cluster | `kind` for a local cluster. Images loaded with `kind load` — no registry to configure |
| Config injection | All environment-specific values via ConfigMap and Secret env vars. Nothing environment-specific baked into the image |
| Data persistence | MySQL backed by a PVC so data survives pod restarts |

---

## 13. What Was Removed From The Original Design

| Removed | Reason |
|---------|--------|
| React frontend, 12 screens | No syllabus item requires a UI. Swagger UI covers API exploration. |
| Nginx reverse proxy | Not required. `kubectl port-forward` gives local access. |
| Jenkins, GitLab CI, Merge Requests | No CI/CD requirement in the syllabus. `deploy.sh` covers the automation requirement. |
| SonarQube | Static analysis not required. |
| Flyway | No production data to migrate. `schema.sql` is sufficient. |
| Ansible | Kubernetes manifests are declarative; an orchestration tool on top is redundant. |
| JWT + refresh tokens + `users` table | HTTP Basic is stateless and needs no token storage or refresh flow. |
| `leave_balances`, `leave_types`, `notifications` tables | Balance is computed, leave type is an enum, notifications are out of scope. |
| `admin` and `notification` modules | Folded into employee/leave controllers. |
| 9 backend modules → 5 | Removed auth, leave-balance, notification, admin as standalone modules. |

**Net:** 9 modules → 5, 9 tables → 4, 8 pipeline tools → 4 (Docker, Kubernetes, Karate, Testcontainers).
