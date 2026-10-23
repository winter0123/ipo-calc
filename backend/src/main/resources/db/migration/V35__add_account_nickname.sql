ALTER TABLE accounts ADD COLUMN nickname VARCHAR(50);

UPDATE accounts
SET nickname = LEFT(COALESCE(NULLIF(BTRIM(name), ''), email), 50)
WHERE nickname IS NULL;

ALTER TABLE accounts ALTER COLUMN nickname SET NOT NULL;
