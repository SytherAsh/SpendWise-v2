-- ML strategy phase (2026-07-19, ADR-014): lets a user permanently correct a wrongly-computed
-- recipient_canonical (a bad merge or a bad split from RecipientCanonicalizationJob's
-- fuzzy-clustering algorithm) via TransactionService#correctPayeeName. Mirrors ml_corrections'
-- role for categorization: the correction is owned and written directly by the Transaction
-- module (no cross-module call to Categorization), and is read back by
-- RecipientCanonicalizationSweep (bypassing RLS via spendwise_jobs) so the override permanently
-- wins over whatever the clustering algorithm recomputes on every subsequent weekly resweep.
--
-- recipient_name/upi_id are nullable to match the identity shape already established by V13/
-- RecipientIdentity (a transaction can have either field null); no UNIQUE constraint on
-- (user_id, recipient_name, upi_id) since Postgres UNIQUE treats NULLs as distinct by default
-- (would allow duplicate NULL-bearing rows for the same user) -- upsert semantics are instead
-- enforced in RecipientCanonicalOverrideRepository via an explicit IS NOT DISTINCT FROM
-- delete-then-insert, the same null-safe matching updateCanonicalForIdentity already uses.
CREATE TABLE recipient_canonicalization_overrides (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_name  VARCHAR,
    upi_id          VARCHAR,
    canonical_name  VARCHAR NOT NULL,
    corrected_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Covers RecipientCanonicalizationSweep's cross-user read.
CREATE INDEX idx_recipient_overrides_user ON recipient_canonicalization_overrides(user_id);

ALTER TABLE recipient_canonicalization_overrides ENABLE ROW LEVEL SECURITY;
ALTER TABLE recipient_canonicalization_overrides FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own recipient overrides"
ON recipient_canonicalization_overrides
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);
