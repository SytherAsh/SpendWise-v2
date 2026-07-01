-- Runs once when the local Postgres container is first initialized
-- (postgres image convention: any *.sql in /docker-entrypoint-initdb.d/).
--
-- Why this role exists: Postgres superusers (and any role with BYPASSRLS)
-- always bypass Row-Level Security, regardless of FORCE ROW LEVEL SECURITY
-- (see V5__row_level_security.sql). The default "postgres" role in this
-- image IS a superuser, so if the app connected as "postgres", every RLS
-- policy would be silently ignored and the safe-fail-deny behavior
-- docs/security.md describes would never actually trigger. spendwise_app
-- is a plain LOGIN role with no superuser/BYPASSRLS attribute, so it is
-- genuinely subject to the tables it owns once FORCE ROW LEVEL SECURITY
-- is set on them.
CREATE ROLE spendwise_app WITH LOGIN PASSWORD 'spendwise_app_password';
GRANT ALL PRIVILEGES ON DATABASE spendwise TO spendwise_app;
GRANT ALL ON SCHEMA public TO spendwise_app;
