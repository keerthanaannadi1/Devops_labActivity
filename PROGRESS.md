# Build Progress

_Last updated: end of session 1_

## Current state

| Area | Status |
|------|--------|
| `ARCHITECTURE.md` | Done — trimmed design, 13 sections |
| Backend scaffold (`pom.xml`, `application.yml`, `schema.sql`, `data.sql`) | Done |
| Entities + repositories (4 tables) | Done |
| Services (leave, attendance, employee, department, reports) | Done |
| Controllers + Spring Security + Swagger | Done |
| `LeaveServiceTest` (13 tests) | Passing |
| `AttendanceServiceTest` (8 tests) | Passing |
| `LeaveControllerTest` (8 tests) | Passing |
| `EmployeeRepositoryTest` (`@DataJpaTest`) | Written, **not yet run** |
| `EmployeeRoleGuardTest` | Written, **broken — see below** |
| Karate API feature files | Not started |
| `karate-js-demo/` | Not started |
| `backend/Dockerfile` | Not started |
| `k8s/*.yaml` (6 manifests) | Not started |
| `devops/deploy.sh` | Not started |

## First task next session

`backend/src/test/java/com/elms/employee/EmployeeRoleGuardTest.java` will **fail to compile**.
Two malformed helpers were left in the `setUp()` block:

```java
when(repository();          // broken
private java.util.Optional<Employee> repository() { ... }   // remove
private void when(java.util.Optional<Employee> value) { ... }  // remove
```

Replace the whole `setUp()` block with:

```java
@BeforeEach
void setUp() {
    plainEmployee = new Employee("Priya Sharma", "priya@elms.local", "hash", Role.EMPLOYEE);
    ReflectionTestUtils.setField(plainEmployee, "id", 1L);
    Mockito.when(repository.findByEmail("priya@elms.local")).thenReturn(Optional.of(plainEmployee));
}
```

Then add `import org.mockito.Mockito;`. The test methods themselves are fine.

## Environment setup (required before any `mvn` command)

```bash
source ~/.local/tools/env.sh
```

This sets `JAVA_HOME` to a locally-installed JDK 21 and puts Maven on `PATH`.

### What was installed and why

| Tool | Location | Reason |
|------|----------|--------|
| JDK 21.0.12.1 (Temurin) | `~/.local/tools/jdk-21.0.12.1+1` | The machine had a **JRE only** — no `javac`, so nothing could compile |
| Maven 3.9.16 | `~/.local/tools/apache-maven-3.9.16` | `mvn` was not installed |

Both were unpacked from tarballs because `sudo` requires a password.

### Still missing — needs action

| Tool | Notes |
|------|-------|
| **Docker** | Requires root. `sudo apt install docker.io`, then verify with `docker info` |
| `kind` | Binary, no root needed once Docker exists |
| `kubectl` | Binary, no root needed |
| Karate CLI | `curl -fsSL https://karate.sh/install.sh \| sh` |

`kind`, `kubectl`, and the Karate CLI can all be installed without root once Docker is running.

## Gotchas discovered

- **Karate version**: `1.5.1` is advertised on the Karate site but is **not published to Maven Central**.
  `pom.xml` uses `1.4.1`, which is the latest real release.
- **`@WebMvcTest` does not load `SecurityConfig`**, so Spring Security's default CSRF protection
  rejects every POST/PUT with 403. Slice tests must add `@Import(SecurityConfig.class)`.
- **Entity ids are null outside JPA.** Unit tests that compare identities
  (`getId().equals(...)`) must seed ids via `ReflectionTestUtils.setField(entity, "id", 1L)`.
- **`l.totalDays()` is not valid in JPQL** — Hibernate cannot call arbitrary Java methods.
  Leave-day totals are summed in Java instead.
- `pom.xml` surefire config includes `**/KarateTestRunner.java` so Karate's `@Suite` runner is
  picked up alongside the Spring tests.

## Seed accounts

Password for every account: `password123`

| Email | Role |
|-------|------|
| `asha@elms.local` | HR_ADMIN |
| `ravi@elms.local` | MANAGER |
| `priya@elms.local` | EMPLOYEE (reports to Ravi) |
| `daniel@elms.local` | EMPLOYEE (reports to Ravi) |

## Remaining build order

1. Fix `EmployeeRoleGuardTest`, run `mvn -B test` — target: all green
2. Add the Testcontainers integration test (`@SpringBootTest` + real MySQL)
3. Karate API feature files + `karate-config.js` + `KarateTestRunner`
4. `karate-js-demo/` (standalone JavaScript + Karate, syllabus item 4)
5. `backend/Dockerfile` (multi-stage), verify `docker build`
6. `k8s/` manifests + `devops/deploy.sh`, deploy to `kind`, scale to 3 replicas
7. Run the Karate suite against the live cluster
