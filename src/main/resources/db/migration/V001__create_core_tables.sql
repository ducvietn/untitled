-- ============================================================
-- TeamUp — Flyway Migration V001
-- Creates all core tables with indexes, FK constraints,
-- unique constraints, and auditing columns.
-- ============================================================

-- V001__create_core_tables.sql
-- Run order: 001

-- ── Users ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    version       BIGINT         NOT NULL DEFAULT 0,
    name          VARCHAR(100)   NOT NULL,
    email         VARCHAR(255)   NOT NULL,
    role          VARCHAR(20)    NOT NULL,
    password_hash VARCHAR(255)   NOT NULL,

    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6),

    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role  ON users(role);


-- ── Groups ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS groups (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    version     BIGINT         NOT NULL DEFAULT 0,
    group_name  VARCHAR(100)   NOT NULL,
    class_id    VARCHAR(50)    NOT NULL,
    leader_id   BIGINT         NOT NULL,

    created_at  DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6),

    CONSTRAINT fk_groups_leader FOREIGN KEY (leader_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_groups_class_name UNIQUE (class_id, group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_groups_class_id  ON groups(class_id);
CREATE INDEX idx_groups_leader_id ON groups(leader_id);


-- ── Group Members (join table) ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS group_members (
    group_id BIGINT NOT NULL,
    user_id   BIGINT NOT NULL,

    CONSTRAINT fk_gm_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_gm_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE,
    CONSTRAINT pk_group_members PRIMARY KEY (group_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ── Tasks ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tasks (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    version        BIGINT         NOT NULL DEFAULT 0,
    task_name      VARCHAR(150)   NOT NULL,
    description    TEXT,
    deadline       DATETIME(6)   NOT NULL,
    progress       INT           NOT NULL DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
    status         VARCHAR(20)   NOT NULL DEFAULT 'TO_DO',
    group_id       BIGINT        NOT NULL,
    assigned_to    BIGINT        NOT NULL,

    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6),

    CONSTRAINT fk_tasks_group       FOREIGN KEY (group_id)    REFERENCES groups(id)  ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assigned_to FOREIGN KEY (assigned_to) REFERENCES users(id)   ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_tasks_group_id    ON tasks(group_id);
CREATE INDEX idx_tasks_assigned_to ON tasks(assigned_to);
CREATE INDEX idx_tasks_status      ON tasks(status);
CREATE INDEX idx_tasks_updated_at  ON tasks(updated_at);


-- ── Submissions ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS submissions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    version      BIGINT         NOT NULL DEFAULT 0,
    file_url     VARCHAR(500)   NOT NULL,
    file_name    VARCHAR(255)   NOT NULL,
    file_size    BIGINT        NOT NULL,
    content_type VARCHAR(100),
    submitted_at DATETIME(6)   NOT NULL,

    task_id      BIGINT        NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6),

    CONSTRAINT fk_submissions_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_submissions_task_id ON submissions(task_id);


-- ── Peer Reviews ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS peer_reviews (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    version     BIGINT         NOT NULL DEFAULT 0,
    score       INT            NOT NULL CHECK (score >= 1 AND score <= 5),
    comment     TEXT,
    reviewed_at DATETIME(6)   NOT NULL,

    group_id    BIGINT        NOT NULL,
    reviewer_id BIGINT        NOT NULL,
    reviewee_id BIGINT        NOT NULL,

    created_at  DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6),

    CONSTRAINT fk_pr_group    FOREIGN KEY (group_id)    REFERENCES groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_pr_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id)  ON DELETE CASCADE,
    CONSTRAINT fk_pr_reviewee FOREIGN KEY (reviewee_id) REFERENCES users(id)  ON DELETE CASCADE,
    CONSTRAINT uk_peer_review_pair UNIQUE (group_id, reviewer_id, reviewee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_peer_reviews_group_id    ON peer_reviews(group_id);
CREATE INDEX idx_peer_reviews_reviewer_id ON peer_reviews(reviewer_id);
CREATE INDEX idx_peer_reviews_reviewee_id ON peer_reviews(reviewee_id);
