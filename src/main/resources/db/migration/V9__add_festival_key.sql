ALTER TABLE festivals
    ADD COLUMN festival_key VARCHAR(64) NULL;

UPDATE festivals
SET festival_key = 'BUSAN_SEA_FESTIVAL'
WHERE contents_id = '142080'
  AND year = 2026
  AND festival_key IS NULL;

UPDATE festivals
SET festival_key = 'BUSAN_INTERNATIONAL_ROCK_FESTIVAL'
WHERE contents_id = '140799'
  AND year = 2026
  AND festival_key IS NULL;
