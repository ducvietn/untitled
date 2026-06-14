-- ============================================================
-- TeamUp — Flyway Migration V004
-- Adds subject-scoping fields to:
--   - classes  (subject_code, subject_name)
--   - groups   (subject_code, subject_name)
--   - users    (subject_code — mandatory for teachers)
--
-- Rationale:
--   Teachers are scoped to their registered subject_code.
--   Students can be members of any group; their user.subject_code remains null.
--   A teacher's user.subject_code must match group.subject_code for override access.
-- ============================================================

-- ── 1. classes: subject_code + subject_name ──────────────────────────────────────
ALTER TABLE classes
    ADD COLUMN IF NOT EXISTS subject_code VARCHAR(20) NOT NULL DEFAULT ''
    AFTER class_code;

ALTER TABLE classes
    ADD COLUMN IF NOT EXISTS subject_name VARCHAR(200) NOT NULL DEFAULT ''
    AFTER subject_code;

CREATE INDEX IF NOT EXISTS idx_classes_subject_code ON classes(subject_code);

-- Prevent duplicate class codes within the same subject
ALTER TABLE classes
    ADD CONSTRAINT uk_classes_subject_code UNIQUE (class_code, subject_code);

-- ── 2. groups: subject_code + subject_name ───────────────────────────────────────
ALTER TABLE groups
    ADD COLUMN IF NOT EXISTS subject_code VARCHAR(20) NOT NULL DEFAULT ''
    AFTER leader_id;

ALTER TABLE groups
    ADD COLUMN IF NOT EXISTS subject_name VARCHAR(200) NOT NULL DEFAULT ''
    AFTER subject_code;

CREATE INDEX IF NOT EXISTS idx_groups_subject_code ON groups(subject_code);

-- ── 3. users: subject_code ───────────────────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS subject_code VARCHAR(20) NULL
    AFTER account_status;

CREATE INDEX IF NOT EXISTS idx_users_subject_code ON users(subject_code);

-- ── 4. Backfill existing data (no-op if already populated) ───────────────────────
-- Run manually against existing rows before deploying:
--
-- UPDATE users SET subject_code = '' WHERE subject_code IS NULL AND role = 'ROLE_STUDENT';
-- UPDATE classes SET subject_code = class_code, subject_name = class_name WHERE subject_code = '' OR subject_code IS NULL;
-- UPDATE groups  SET subject_code = classEntity.subject_code,
--                    subject_name = classEntity.subject_name
--              FROM classes classEntity
--              WHERE groups.class_id = classEntity.id
--                AND (groups.subject_code = '' OR groups.subject_code IS NULL);
