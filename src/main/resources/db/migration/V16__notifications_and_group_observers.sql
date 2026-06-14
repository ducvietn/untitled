-- ================================================================
-- TeamUp — Notification System & Observer Role DDL
-- MySQL 8.0+ / Flyway-compatible migration
-- ================================================================

-- ─────────────────────────────────────────────────────────────────
-- 1.  NOTIFICATIONS TABLE
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    title        VARCHAR(200) NOT NULL,
    message      TEXT         NOT NULL,
    type         VARCHAR(30)  NOT NULL  DEFAULT 'APP_ALERT',
    is_read      BOOLEAN      NOT NULL  DEFAULT FALSE,
    created_at   DATETIME     NOT NULL  DEFAULT CURRENT_TIMESTAMP,

    -- Index for the most common query: "unread notifications for user X, newest first"
    INDEX idx_notifications_user_unread (user_id, is_read, created_at DESC),

    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────
-- 2.  GROUP_OBSERVERS MAPPING TABLE
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS group_observers (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id    BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    assigned_at DATETIME    NOT NULL  DEFAULT CURRENT_TIMESTAMP,
    active      BOOLEAN     NOT NULL  DEFAULT TRUE,

    -- A user can observe the same group only once (no duplicate rows)
    CONSTRAINT uk_group_observer UNIQUE (group_id, user_id),

    CONSTRAINT fk_go_group
        FOREIGN KEY (group_id) REFERENCES groups(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_go_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,

    -- Index for fast lookup: "all groups this user observes"
    INDEX idx_group_observers_user (user_id, active),

    -- Index for fast lookup: "all observers of a group"
    INDEX idx_group_observers_group (group_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
