-- ============================================================
-- TeamUp — Flyway Migration V002
-- Extends schema with: classes, registration_requests,
-- user_invitations, and account_status on users.
-- Also renames groups.class_id → FK to classes table.
-- ============================================================

-- V002__extend_schema.sql
-- Run order: 002

-- ── 1. Add account_status to users ────────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
    AFTER password_hash;

CREATE INDEX IF NOT EXISTS idx_users_account_status ON users(account_status);


-- ── 2. Classes table ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS classes (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    version       BIGINT         NOT NULL DEFAULT 0,
    class_name    VARCHAR(150)   NOT NULL,
    class_code    VARCHAR(50)    NOT NULL,
    description   VARCHAR(500),
    semester      VARCHAR(30),
    owner_id      BIGINT         NOT NULL,
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6),

    CONSTRAINT fk_classes_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_classes_code  UNIQUE (class_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_classes_owner_id ON classes(owner_id);


-- ── 3. Update groups: drop old class_id, add FK to classes ───────────────────
-- First drop the old plain-text column and its old unique constraint
ALTER TABLE groups
    DROP COLUMN IF EXISTS class_id;

-- Add new FK column (JPA will manage the column name via @JoinColumn(name = "class_id"))
-- Flyway: we recreate the column as a FK reference
ALTER TABLE groups
    ADD COLUMN class_id BIGINT NOT NULL AFTER group_name;

ALTER TABLE groups
    ADD CONSTRAINT fk_groups_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE RESTRICT;


-- ── 4. Registration Requests ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS registration_requests (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    version          BIGINT         NOT NULL DEFAULT 0,
    name             VARCHAR(100)   NOT NULL,
    email            VARCHAR(255)   NOT NULL,
    password_hash    VARCHAR(255)   NOT NULL,
    role             VARCHAR(20)   NOT NULL DEFAULT 'ROLE_TEACHER',
    institution      VARCHAR(500),
    status           VARCHAR(20)   NOT NULL DEFAULT 'REGISTERING',
    fast_track       BOOLEAN       NOT NULL DEFAULT FALSE,
    rejection_reason VARCHAR(255),
    reviewed_by      BIGINT,
    reviewed_at      DATETIME(6),

    requester_id     BIGINT        NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6),

    CONSTRAINT fk_reg_requests_requester  FOREIGN KEY (requester_id) REFERENCES users(id)  ON DELETE CASCADE,
    CONSTRAINT fk_reg_requests_reviewed_by FOREIGN KEY (reviewed_by)    REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_reg_request_email         UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_reg_requests_requester_id ON registration_requests(requester_id);
CREATE INDEX IF NOT EXISTS idx_reg_requests_status       ON registration_requests(status);


-- ── 5. User Invitations ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_invitations (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    version          BIGINT         NOT NULL DEFAULT 0,
    token            VARCHAR(64)    NOT NULL,
    email            VARCHAR(255)   NOT NULL,
    student_name     VARCHAR(100),
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    expires_at       DATETIME(6)    NOT NULL,
    accepted_at      DATETIME(6),

    class_id         BIGINT        NOT NULL,
    invited_user_id  BIGINT,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6),

    CONSTRAINT fk_invitations_class FOREIGN KEY (class_id)      REFERENCES classes(id) ON DELETE CASCADE,
    CONSTRAINT fk_invitations_user   FOREIGN KEY (invited_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_invitation_token    UNIQUE (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IF NOT EXISTS idx_invitations_class_id ON user_invitations(class_id);
CREATE INDEX IF NOT EXISTS idx_invitations_email    ON user_invitations(email);
