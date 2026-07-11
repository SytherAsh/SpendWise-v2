-- ML Strategy phase (2026-07-11): recurring-payment detection moves from the
-- exact-match rule (E6-S1-T1) to an ML classifier (ml/training/train_recurring.py)
-- that judges loosened candidate groups (2+ occurrences, up to 400-day window,
-- +/-40% amount tolerance -- see RecurringPaymentDetector.java's updated
-- constants) instead of gating on the old strict thresholds directly.

-- cadence/confidence_score are populated when an EMI is created from an
-- ML-confirmed detection (AlertsServiceImpl#confirmRecurringPayment); both
-- remain NULL for manually-entered EMIs, same nullability story as
-- source_transaction_id already has.
ALTER TABLE emis ADD COLUMN cadence VARCHAR
    CHECK (cadence IN ('weekly', 'biweekly', 'monthly', 'quarterly', 'annual', 'irregular'));
ALTER TABLE emis ADD COLUMN confidence_score FLOAT;

-- Mirrors ml_corrections' role for categorization: every confirm/dismiss of a
-- recurring_payment alert becomes a labeled training example for the next
-- retrain cycle (ml/api/retrain_recurring.py), replacing the bootstrap labels
-- (training/recurring_labels.py) with real user judgment over time. Stores
-- the exact feature snapshot from prediction time, not a live re-computation,
-- so a correction stays meaningful even if the underlying transactions are
-- later edited or deleted (representative_transaction_id is ON DELETE SET
-- NULL for the same reason ml_corrections doesn't cascade-delete its label).
CREATE TABLE recurring_corrections (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    representative_transaction_id  UUID REFERENCES transactions(id) ON DELETE SET NULL,
    occurrence_count               INT NOT NULL,
    interval_mean_days             FLOAT NOT NULL,
    interval_cv                    FLOAT NOT NULL,
    amount_mean                    FLOAT NOT NULL,
    amount_cv                      FLOAT NOT NULL,
    span_days                      FLOAT NOT NULL,
    days_since_last_occurrence     FLOAT NOT NULL,
    was_recurring                  BOOLEAN NOT NULL,
    corrected_at                   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Covers the weekly retraining job's incremental read, same pattern as
-- idx_ml_corrections_date.
CREATE INDEX idx_recurring_corrections_date ON recurring_corrections(corrected_at);

ALTER TABLE recurring_corrections ENABLE ROW LEVEL SECURITY;
ALTER TABLE recurring_corrections FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own recurring corrections"
ON recurring_corrections
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);
