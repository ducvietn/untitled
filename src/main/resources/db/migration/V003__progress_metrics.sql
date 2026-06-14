-- ============================================================
-- TeamUp — Flyway Migration V003
-- Adds estimated_hours (weight) to tasks, plus
-- self-referential depends_on_task_id FK for bottleneck detection.
-- ============================================================

-- V003__progress_metrics.sql
-- Run order: 003

-- ── 1. Add estimated_hours weight column ───────────────────────────────────────
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS estimated_hours DOUBLE NOT NULL DEFAULT 1.0
    AFTER status;

CREATE INDEX IF NOT EXISTS idx_tasks_estimated_hours ON tasks(estimated_hours);


-- ── 2. Add self-referential depends_on_task_id FK ─────────────────────────────
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS depends_on_task_id BIGINT
    AFTER estimated_hours;

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_depends_on
        FOREIGN KEY (depends_on_task_id) REFERENCES tasks(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_tasks_depends_on ON tasks(depends_on_task_id);
