-- UI/UX polish phase: Transfer counterparty enrichment, requested by the project owner
-- 2026-07-09. This is the feature already sketched (and deliberately deferred) in
-- docs/decisions.md ADR-010 and docs/architecture.md "Future Enhancement: Counterparty
-- Metadata Enrichment" — now being built per that sketch.
--
-- Per ADR-010, this is NOT a new ML category and NOT a new `categories` row. `contacts` is
-- a separate, per-user metadata table. It is never joined server-side onto `transactions`
-- or `transaction_categories` — the frontend fetches it via GET /api/v1/contacts and matches
-- it against transactions.recipient_name/upi_id client-side (docs/api.md "Contacts").
CREATE TABLE contacts (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                    VARCHAR NOT NULL,
    relationship_type       VARCHAR NOT NULL CHECK (relationship_type IN ('family', 'friend', 'self', 'settlement')),
    recipient_name_pattern  VARCHAR,  -- matched case-insensitively against transactions.recipient_name
    upi_id                  VARCHAR,  -- matched exactly against transactions.upi_id
    phone_number            VARCHAR,  -- matched as a prefix of transactions.upi_id (common <phone>@bank UPI format)
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    -- A contact with no identifier at all could never match a transaction, so it can only
    -- ever be dead data — reject it at write time instead of allowing a silently useless row.
    CONSTRAINT chk_contact_has_identifier CHECK (
        recipient_name_pattern IS NOT NULL OR upi_id IS NOT NULL OR phone_number IS NOT NULL
    )
);

CREATE INDEX idx_contacts_user_id ON contacts(user_id);

ALTER TABLE contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE contacts FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own contacts"
ON contacts
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);
