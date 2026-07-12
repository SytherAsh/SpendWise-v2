-- Adds Bills to the categories table. See docs/requirements.md and
-- ml/labeling/CATEGORY_GUIDELINES.md for why: recurring household utility
-- bills (electricity, gas, water, maintenance/society charges) had no home
-- among the original 12 categories and were landing in Miscellaneous.
--
-- Same situation Sports & Fitness is already in: added to the schema ahead of
-- having any labeled training examples, so it starts out reachable only via
-- manual category selection until user corrections build up enough examples
-- for a future retraining cycle.

INSERT INTO categories (id, name, icon) VALUES
    (13, 'Bills', 'receipt_long');

SELECT setval(pg_get_serial_sequence('categories', 'id'), 13);
