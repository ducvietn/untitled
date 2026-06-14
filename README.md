# TeamUp — Group Project Management Platform

## Project Overview

TeamUp is a Spring Boot backend that acts as an objective "referee" for student group projects.
It tracks individual contributions, enforces a structured approval workflow, manages teacher onboarding,
and generates transparent grading reports for teachers.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3.0 |
| ORM | Spring Data JPA / Hibernate 6 |
| Database | MySQL 8.x (swap driver for PostgreSQL) |
| Migrations | Flyway |
| Security | Spring Security + JWT (jjwt 0.12.5) |
| DTO Mapping | MapStruct 1.5.5 |
| Reporting | Apache POI (Excel) + iText (PDF) |
| Build | Maven 3.9+ |

---

## Project Structure

```
src/main/java/com/teamup/teamup/
│
├── TeamUpApplication.java              # Boot entry point + @EnableScheduling
│
├── config/
│   ├── JpaConfig.java                  # @EnableJpaAuditing
│   └── SecurityConfig.java             # (Step 2 — JWT filter chain)
│
├── entity/
│   ├── BaseEntity.java                # id + @Version (optimistic lock — ALL entities)
│   ├── AuditableEntity.java           # created_at + updated_at interface
│   ├── User.java                      # Role, AccountStatus, ownedClasses
│   ├── Group.java                     # ManyToOne → Class, leader, Set<User> members
│   ├── Task.java                      # progress 0–100, TaskStatus, @Version
│   ├── Submission.java                # fileUrl, fileName, fileSize
│   ├── PeerReview.java                # score 1–5, masked reviewee_id
│   ├── Class.java                     # className, classCode, owner (Teacher)
│   ├── RegistrationRequest.java       # Teacher onboarding (ROLE_ADMIN review)
│   └── UserInvitation.java            # Class roster (CSV/email invite)
│
└── enums/
    ├── Role.java                      # ROLE_STUDENT | ROLE_TEACHER | ROLE_ADMIN
    ├── AccountStatus.java             # ACTIVE | PENDING_APPROVAL | SUSPENDED | REJECTED
    ├── TaskStatus.java                # TO_DO | IN_PROGRESS | PENDING_REVIEW | DONE
    ├── RegistrationStatus.java        # REGISTERING | FAST_TRACK | APPROVED | REJECTED
    └── InvitationStatus.java          # PENDING | ACCEPTED | EXPIRED | CANCELLED
```

```
src/main/resources/
├── application.yml                    # Default profile (dev)
├── application-prod.yml              # Production overrides
└── db/migration/
    ├── V001__create_core_tables.sql   # users, groups, tasks, submissions, peer_reviews
    └── V002__extend_schema.sql        # classes, registration_requests, user_invitations, account_status
```

---

## Database Schema

### V001 — Core Tables (`V001__create_core_tables.sql`)

#### users
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT |
| version | BIGINT | NOT NULL DEFAULT 0 |
| name | VARCHAR(100) | NOT NULL |
| email | VARCHAR(255) | NOT NULL **UNIQUE** |
| role | VARCHAR(20) | NOT NULL |
| password_hash | VARCHAR(255) | NOT NULL |
| **account_status** | VARCHAR(20) | NOT NULL DEFAULT 'ACTIVE' *(added V002)* |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | — |
**Indexes:** `idx_users_email`, `idx_users_role`, `idx_users_account_status`

#### classes *(V002 — new)*
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT |
| version | BIGINT | NOT NULL DEFAULT 0 |
| class_name | VARCHAR(150) | NOT NULL |
| class_code | VARCHAR(50) | NOT NULL **UNIQUE** |
| description | VARCHAR(500) | — |
| semester | VARCHAR(30) | — |
| owner_id | BIGINT | FK → users(id), NOT NULL |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | — |
**Indexes:** `idx_classes_owner_id`

#### groups *(updated V002 — class_id is now FK, not VARCHAR)*
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT |
| version | BIGINT | NOT NULL DEFAULT 0 |
| group_name | VARCHAR(100) | NOT NULL |
| **class_id** | BIGINT | FK → classes(id), NOT NULL |
| leader_id | BIGINT | FK → users(id), NOT NULL |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | — |
**Indexes:** `idx_groups_class_id`, `idx_groups_leader_id`
**Unique:** `(class_id, group_name)` — unique group name per class

#### group_members (join table — unchanged)
| Column | Type | Constraints |
|---|---|---|
| group_id | BIGINT | FK → groups(id) ON DELETE CASCADE |
| user_id | BIGINT | FK → users(id) ON DELETE CASCADE |
**PK:** `(group_id, user_id)`

#### tasks (unchanged)
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT |
| version | BIGINT | NOT NULL DEFAULT 0 |
| task_name | VARCHAR(150) | NOT NULL |
| description | TEXT | — |
| deadline | DATETIME(6) | NOT NULL |
| progress | INT | NOT NULL DEFAULT 0, CHECK 0–100 |
| status | VARCHAR(20) | NOT NULL DEFAULT 'TO_DO' |
| group_id | BIGINT | FK → groups(id), NOT NULL |
| assigned_to | BIGINT | FK → users(id), NOT NULL |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | — |
**Indexes:** `idx_tasks_group_id`, `idx_tasks_assigned_to`, `idx_tasks_status`, `idx_tasks_updated_at`

#### submissions (unchanged)
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT |
| version | BIGINT | NOT NULL DEFAULT 0 |
| file_url | VARCHAR(500) | NOT NULL |
| file_name | VARCHAR(255) | NOT NULL |
| file_size | BIGINT | NOT NULL |
| content_type | VARCHAR(100) | — |
| submitted_at | DATETIME(6) | NOT NULL |
| task_id | BIGINT | FK → tasks(id), NOT NULL |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | — |
**Indexes:** `idx_submissions_task_id`

#### peer_reviews (unchanged)
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT |
| version | BIGINT | NOT NULL DEFAULT 0 |
| score | INT | NOT NULL, CHECK 1–5 |
| comment | TEXT | — |
| reviewed_at | DATETIME(6) | NOT NULL |
| group_id | BIGINT | FK → groups(id), NOT NULL |
| reviewer_id | BIGINT | FK → users(id), NOT NULL |
| reviewee_id | BIGINT | FK → users(id), NOT NULL |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | — |
**Indexes:** `idx_peer_reviews_group_id`, `idx_peer_reviews_reviewer_id`, `idx_peer_reviews_reviewee_id`
**Unique:** `(group_id, reviewer_id, reviewee_id)`

### V002 — Extension Tables (`V002__extend_schema.sql`)

#### registration_requests *(new)*
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT |
| version | BIGINT | NOT NULL DEFAULT 0 |
| name | VARCHAR(100) | NOT NULL |
| email | VARCHAR(255) | NOT NULL **UNIQUE** |
| password_hash | VARCHAR(255) | NOT NULL |
| role | VARCHAR(20) | NOT NULL DEFAULT 'ROLE_TEACHER' |
| institution | VARCHAR(500) | — |
| status | VARCHAR(20) | NOT NULL DEFAULT 'REGISTERING' |
| fast_track | BOOLEAN | NOT NULL DEFAULT FALSE |
| rejection_reason | VARCHAR(255) | — |
| reviewed_by | BIGINT | FK → users(id), nullable |
| reviewed_at | DATETIME(6) | — |
| requester_id | BIGINT | FK → users(id), NOT NULL |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | — |
**Indexes:** `idx_reg_requests_requester_id`, `idx_reg_requests_status`

#### user_invitations *(new)*
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT |
| version | BIGINT | NOT NULL DEFAULT 0 |
| token | VARCHAR(64) | NOT NULL **UNIQUE** |
| email | VARCHAR(255) | NOT NULL |
| student_name | VARCHAR(100) | — |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PENDING' |
| expires_at | DATETIME(6) | NOT NULL |
| accepted_at | DATETIME(6) | — |
| class_id | BIGINT | FK → classes(id), NOT NULL |
| invited_user_id | BIGINT | FK → users(id), nullable |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | — |
**Indexes:** `idx_invitations_class_id`, `idx_invitations_email`

### V003 — Progress Metrics (`V003__progress_metrics.sql`)

#### tasks *(updated V003 — added estimated_hours + depends_on_task_id)*
| Column | Type | Constraints |
|---|---|---|
| **estimated_hours** | DOUBLE | NOT NULL DEFAULT 1.0 — weight for weighted contribution |
| **depends_on_task_id** | BIGINT | FK → tasks(id), nullable — self-referential dependency FK |
**Indexes:** `idx_tasks_estimated_hours`, `idx_tasks_depends_on`

---

## Algorithm Specification

### Feature 9 — Weighted Contribution Algorithm

**Formula:**

$$
Contribution(u,g) = \frac{\sum_{i=1}^{n} Progress_i \times Weight_i}{\sum_{i=1}^{n} Weight_i}
$$

Where:
- $n$ = number of tasks assigned to user $u$ in group $g$
- $Progress_i$ = 0–100 (clamped)
- $Weight_i$ = `estimated_hours` (defaults to 1.0 if null/zero)

**Complexity:**

| | Complexity | Reason |
|---|---|---|
| **Time** | O(n) | Single-pass Java Stream over n tasks; two scalar accumulators |
| **Space** | O(1) | `WeightedAccumulator` holds 4 primitives; detail list is O(n) output |

**Edge cases:**
- 0 tasks → contribution = 0.0
- `estimated_hours` = null/0 → fallback to 1.0
- `progress` out of range → clamped to [0, 100]
- `totalWeight = 0` → fallback to simple mean

**Implementation:** `ProgressCalculationService.calculateContribution(User, Long)`
Uses a mutable `WeightedAccumulator` inside `Stream.reduce()` — avoids O(n) intermediate object allocation from immutable-pair reduction.

---

### Feature 10 — Group Burndown Chart

**Expected progress per day D:**

$$
Expected(D) = clamp\!\left(100 \times \frac{D - start}{deadline - start},\; 0,\; 100\right)
$$

**Actual progress:** For current/past dates, uses cumulative `DONE` task count / total tasks. In production, replace with a `TaskProgressSnapshot` history table for true historical accuracy.

**Safety cap:** Series length capped at 180 days (`MAX_BURNDOWN_DAYS`) to prevent memory overflow on misconfigured data.

**Complexity:** O(n log n) — O(n) task fetch + O(n) date-series generation + O(n log n) sort for member contributions.

---

### Feature 11 — Bottleneck Detection

**Bottleneck conditions (ALL must be true):**

1. `estimated_hours ≥ 4.0` OR deadline within 3 days (urgent)
2. `progress < 50%`
3. Has dependents OR is urgent

**Severity score:**

$$
Severity(t) = estimatedHours_t \times \left(1 - \frac{progress_t}{100}\right)
$$

- 0% progress + 20h = severity 20 (most critical)
- 80% progress + 20h = severity 4 (nearly done)

**Dependency graph:** Built lazily per call via `findTasksWithDependency()`. O(n) query → O(n) adjacency map → O(n log n) sort. No caching (stale-safe for typical call frequency).

**Complexity:** O(n log n) — dominated by the severity sort.

---

## Key Design Decisions

### Optimistic Locking
Every entity extends `BaseEntity` which carries a `@Version Long` field.
Hibernate auto-increments it on every UPDATE.
Concurrent modifications → `OptimisticLockException` (HTTP 409 Conflict).

### Auditing
`AuditableEntity` interface provides `created_at` / `updated_at`.
Combined with `@EnableJpaAuditing`, Hibernate auto-populates these on every persist/merge.
No manual timestamp management required.

### Teacher Onboarding Flow
```
Student registers  →  User (ACTIVE, ROLE_STUDENT)           → can login immediately
Teacher registers →  User (PENDING_APPROVAL, ROLE_TEACHER)  → cannot login
                   + RegistrationRequest (REGISTERING | FAST_TRACK)
                                                              → ROLE_ADMIN reviews
                                                              → Approved: User (ACTIVE, ROLE_TEACHER)
                                                              → Rejected: User deleted, RegRequest (REJECTED)
```

### Academic Domain Fast-Track
When email matches `.*@(*.edu.vn|vnu.edu.vn|usth.edu.vn)$` (configurable),
`fast_track = true`. These requests sort first in the admin queue.

### Class → Group Hierarchy
`Class` is the top-level container. `Group.classEntity` FK replaces the old loose `classId VARCHAR`.
All teacher override powers (deadline extension, task unlock, file access) are scoped to a Class.

### Role-Based Access Control
| Capability | ROLE_STUDENT | ROLE_TEACHER | ROLE_ADMIN |
|---|---|---|---|
| Own tasks & submissions | ✓ | — | — |
| Approve/reject tasks (as leader) | ✓ | — | — |
| Submit peer reviews | ✓ | — | — |
| Create classes | — | ✓ | ✓ |
| Bulk-import students | — | ✓ | ✓ |
| God Eye Dashboard | — | ✓ | ✓ |
| Master Override APIs | — | ✓ | — |
| Generate reports | — | ✓ | ✓ |
| Approve/reject teacher registrations | — | — | ✓ |

---

## Next Steps

- **Step 4 — Security & DTOs:** JWT authentication, `SecurityConfig`, `UserDetailsService`,
  all DTOs, MapStruct mappers, global exception handler (`@RestControllerAdvice`),
  `ApiResponse<T>` wrapper.
- **Step 5 — Student APIs:** Controllers and Service layer for Features 1–5
  (Task progress/file upload, Leader approval workflow, Peer review, Cron referee).
- **Step 6 — Teacher & Admin APIs:** Registration approval, Class management,
  CSV bulk-import, God Eye Dashboard (Feature 7), Master Override (Feature 8),
  PDF/Excel report export.

---

## Running the Application

```bash
# 1. Ensure MySQL is running and create the database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS teamup_db;"

# 2. Run the application (Flyway auto-applies V001 then V002)
./mvnw spring-boot:run

# 3. For production
JWT_SECRET=<strong-secret> DB_HOST=<prod-host> ./mvnw spring-boot:run -Dspring.profiles.active=prod
```
