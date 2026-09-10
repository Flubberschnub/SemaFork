-- Only the provider's stable ID is persisted. Place content is fetched for display.
ALTER TABLE suggestion
    ADD COLUMN google_place_id VARCHAR(255),
    ALTER COLUMN name DROP NOT NULL;

ALTER TABLE suggestion ADD CONSTRAINT ck_suggestion_source CHECK (
    (google_place_id IS NULL AND name IS NOT NULL AND length(btrim(name)) > 0)
    OR
    (google_place_id IS NOT NULL AND length(btrim(google_place_id)) > 0 AND name IS NULL)
);

-- Different locations of the same chain are different candidates.
CREATE UNIQUE INDEX uq_suggestion_party_google_place
    ON suggestion (party_id, google_place_id)
    WHERE google_place_id IS NOT NULL;
-- Existing normalized-name uniqueness continues to protect manual candidates.
