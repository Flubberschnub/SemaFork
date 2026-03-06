-- Store winner of party
ALTER TABLE party ADD COLUMN winner_suggestion_id BIGINT NULL REFERENCES suggestion(id);

-- Votes table
CREATE TABLE vote (
    id BIGSERIAL PRIMARY KEY,
    party_id BIGINT NOT NULL REFERENCES party(id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL REFERENCES party_member(id) ON DELETE CASCADE,
    suggestion_id BIGINT NOT NULL REFERENCES suggestion(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);