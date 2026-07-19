-- Admin-configurable background-job schedules (ML strategy phase, 2026-07-19, ADR-018).
-- Replaces every hardcoded @Scheduled cron/fixedRate in the app with a DB-backed schedule an
-- admin can edit at runtime (com.spendwise.common.schedule.DynamicJobScheduler reads this table
-- to compute each job's next run, and re-reads it live on every computation).
--
-- No RLS: system-wide configuration, not user data -- same precedent as `categories` (V2), which
-- also has no user_id column and no RLS policy.
--
-- Shape mirrors the two schedule kinds the admin UI's structured picker offers: INTERVAL ("every
-- N minutes/hours/days", for the three frequent jobs) and WEEKLY ("every <day> at <hour> UTC",
-- for the two weekly ML jobs). The check constraint enforces that a row has exactly the columns
-- its own schedule_type needs, not a mix of both shapes' fields.
CREATE TABLE job_schedules (
    job_key        VARCHAR PRIMARY KEY,
    display_name   VARCHAR NOT NULL,
    schedule_type  VARCHAR NOT NULL CHECK (schedule_type IN ('INTERVAL', 'WEEKLY')),
    interval_value INTEGER CHECK (interval_value IS NULL OR interval_value >= 1),
    interval_unit  VARCHAR CHECK (interval_unit IN ('MINUTES', 'HOURS', 'DAYS')),
    day_of_week    VARCHAR CHECK (day_of_week IN ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN')),
    hour_of_day    INTEGER CHECK (hour_of_day IS NULL OR hour_of_day BETWEEN 0 AND 23),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_job_schedule_shape CHECK (
        (schedule_type = 'INTERVAL' AND interval_value IS NOT NULL AND interval_unit IS NOT NULL
            AND day_of_week IS NULL AND hour_of_day IS NULL)
        OR
        (schedule_type = 'WEEKLY' AND day_of_week IS NOT NULL AND hour_of_day IS NOT NULL
            AND interval_value IS NULL AND interval_unit IS NULL)
    )
);

-- Seed values match what was previously hardcoded, with one deliberate exception:
-- alert_evaluation was hardcoded to 1 MINUTE with a "TESTING ONLY -- restore to 30 before any
-- real use" comment that was never reverted (docs/spec/architecture.md's Background Jobs table
-- always documented 30 minutes as the real value). This migration is that revert.
INSERT INTO job_schedules (job_key, display_name, schedule_type, day_of_week, hour_of_day) VALUES
    ('canonicalization', 'Recipient canonicalization sweep', 'WEEKLY', 'SUN', 4),
    ('ml_retrain', 'ML model retrain', 'WEEKLY', 'SUN', 3);

INSERT INTO job_schedules (job_key, display_name, schedule_type, interval_value, interval_unit) VALUES
    ('categorization_retry', 'Categorization retry', 'INTERVAL', 30, 'MINUTES'),
    ('alert_evaluation', 'Alert + recurring-payment evaluator', 'INTERVAL', 30, 'MINUTES'),
    ('recommendation_generation', 'Recommendation generator', 'INTERVAL', 6, 'HOURS');
