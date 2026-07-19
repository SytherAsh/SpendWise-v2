-- Soft delete for transactions (DELETE /api/v1/transactions/:id, Transactions page redesign).
--
-- A user-initiated delete sets deleted_at rather than removing the row outright, preserving
-- an audit trail for financial data and leaving the door open for recovery. Every read against
-- transactions (list/detail, budget progress, alerts/recurring detection, analytics, ML
-- correction retraining) is updated in the same change to exclude deleted_at IS NOT NULL rows,
-- so a deleted transaction behaves as fully gone everywhere it's consumed, not just in the
-- transactions list.
--
-- Ingest-time dedup (TransactionRepository#existsBySecondaryKey and the transaction_id unique
-- index below) deliberately still counts a soft-deleted row as "existing" — this stops a
-- re-synced SMS from resurrecting a transaction the user explicitly deleted.
--
-- No RLS policy change needed: the column lives on the already-policied transactions table
-- (V5), so the existing per-user policy covers it — RLS scopes by user_id, not deletion state.

ALTER TABLE transactions ADD COLUMN deleted_at TIMESTAMP;
CREATE INDEX idx_transactions_active ON transactions(user_id, transaction_date DESC) WHERE deleted_at IS NULL;
