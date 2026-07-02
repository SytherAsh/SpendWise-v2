-- Dedicated role for @Scheduled background jobs that must read across ALL users
-- (categorization retry E4-S3-T3, ML retraining E4-S3-T4, and future Epic 5/8
-- jobs) -- see V5__row_level_security.sql's header comment ("The Admin module
-- needs cross-user reads -- how that coexists with FORCE RLS ... is an open
-- question deferred to Epic 11, not resolved by this migration") and Epic 4's
-- close-out note in implementation/tracking/STATUS.md for the full context.
--
-- BYPASSRLS is a real, audited exception scoped to exactly this one role.
-- spendwise_app (used for every normal request-handling query, i.e. anything
-- reached from a controller) is completely unaffected and stays fully
-- RLS-enforced -- only Spring beans explicitly wired to the separate "jobs"
-- DataSource (see JobsDataSourceConfig) ever connect as spendwise_jobs, and
-- only scheduled-job classes use that DataSource.
--
-- Role membership (not a one-time GRANT on today's tables) so spendwise_jobs
-- automatically inherits spendwise_app's privileges on tables Flyway creates
-- LATER too -- db-init scripts run once, before any migration has created a
-- single table.
CREATE ROLE spendwise_jobs WITH LOGIN PASSWORD 'spendwise_jobs_password' BYPASSRLS;
GRANT spendwise_app TO spendwise_jobs;
