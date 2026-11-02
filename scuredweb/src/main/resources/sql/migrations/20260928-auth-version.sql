-- Apply once as schema owner before deploying the new WAR. No password values selected.
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
ALTER TABLE RW_USER ADD (AUTH_VERSION NUMBER DEFAULT 1 NOT NULL);
SELECT COUNT(*) AS INVALID_AUTH_VERSIONS FROM RW_USER WHERE AUTH_VERSION IS NULL OR AUTH_VERSION < 1;
-- Rollback application code only; retain the additive column and current credential versions.
