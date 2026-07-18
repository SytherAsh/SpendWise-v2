-- Adds a canonical (normalized/deduplicated) recipient name to transactions.
--
-- The raw recipient_name column preserves exactly what each bank/SMS rendered
-- per transaction, which for a single real payee is often several spellings
-- ("SWIGGY", "Swiggy Bangalore", "swiggy@icici"), split-name artifacts, and
-- truncations. recipient_canonical collapses those to one stable name per
-- payee so recurring-payment detection groups them together and the UI can
-- display a clean name.
--
-- Populated by RecipientCanonicalizationJob (ML strategy phase) calling the
-- FastAPI /normalize-recipients endpoint per user; nullable because it is only
-- assigned once that batch job has run over a user's history. A transaction
-- whose recipient_canonical is still null falls back to recipient_name
-- everywhere (grouping, display), so the column is purely additive: nothing
-- breaks before the job first runs, and raw recipient_name is never mutated.
--
-- No RLS policy change needed: the column lives on the already-policied
-- transactions table (V5), so every existing per-user policy covers it.

ALTER TABLE transactions ADD COLUMN recipient_canonical VARCHAR;
