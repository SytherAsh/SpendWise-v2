-- Adds Medical and Fees & Debt to the categories table. See docs/requirements.md
-- and ml/labeling/CATEGORY_GUIDELINES.md for why: medical-shop and fee/debt
-- transactions had no home among the original 10 categories during ML
-- training-data labeling and were being forced into Miscellaneous.

INSERT INTO categories (id, name, icon) VALUES
    (11, 'Medical',     'local_hospital'),
    (12, 'Fees & Debt', 'request_quote');

SELECT setval(pg_get_serial_sequence('categories', 'id'), 12);
