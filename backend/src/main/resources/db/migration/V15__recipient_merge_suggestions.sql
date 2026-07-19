-- Merge Payees human-review queue (ML strategy phase, 2026-07-19) -- persists ambiguous
-- anchor/candidate identity pairs FastAPI's /normalize-recipients now surfaces (the fuzzy
-- clustering / prefix-chain merge considered these close but did not confidently auto-merge,
-- e.g. bare "SAMEER" vs "SAMEER SAWANT" vs "SAMEER BALIRAM SAWA") so a user can confirm or
-- reject each one via PayeeMergeQueueController, instead of the algorithm silently leaving
-- them unmerged forever with no way for the user to weigh in.
--
-- One status-tracked table, not two ("pending" + "confirmed different"): RecipientCanonicalization-
-- Sweep's dedup check ("has this exact pair already been suggested or resolved, in either
-- direction?") needs to look across every status regardless, so a second table would need the
-- same cross-table existence check for no isolation benefit.
--
-- anchor_canonical_name is captured at insert time from the same ML response that produced this
-- suggestion, so confirming "same" never needs a second lookup -- it reuses
-- TransactionService#correctPayeeIdentity directly with this value.
--
-- No UNIQUE constraint (same reasoning as V14): recipient_name/upi_id are nullable and Postgres
-- UNIQUE treats NULLs as distinct. Dedup on resweep is enforced in
-- RecipientMergeSuggestionRepository via an explicit existence check, comparing the pair as an
-- UNORDERED set (the algorithm can flip which identity it calls "anchor" between resweeps as
-- frequencies shift, so matching only on (anchor, candidate) literally would let a rejected pair
-- resurface forever just because the roles swapped).
CREATE TABLE recipient_merge_suggestions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    anchor_name            VARCHAR NOT NULL,
    anchor_upi_id          VARCHAR,
    anchor_canonical_name  VARCHAR NOT NULL,
    candidate_name         VARCHAR NOT NULL,
    candidate_upi_id       VARCHAR,
    score                  INTEGER NOT NULL,
    reason                 VARCHAR NOT NULL,
    status                 VARCHAR NOT NULL DEFAULT 'PENDING',
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at            TIMESTAMP,
    CONSTRAINT chk_merge_suggestion_status CHECK (status IN ('PENDING', 'CONFIRMED_SAME', 'CONFIRMED_DIFFERENT'))
);

-- Covers both the per-user pending-queue read and RecipientCanonicalizationSweep's cross-user
-- dedup read (which reads every status, not just PENDING).
CREATE INDEX idx_merge_suggestions_user_status ON recipient_merge_suggestions(user_id, status);

ALTER TABLE recipient_merge_suggestions ENABLE ROW LEVEL SECURITY;
ALTER TABLE recipient_merge_suggestions FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own merge suggestions"
ON recipient_merge_suggestions
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);
