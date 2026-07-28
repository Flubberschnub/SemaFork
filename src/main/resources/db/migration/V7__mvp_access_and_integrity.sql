ALTER TABLE party
    ADD COLUMN join_code VARCHAR(8),
    ADD COLUMN host_token VARCHAR(64);

UPDATE party
SET join_code = upper(substring(md5(id::text || random()::text), 1, 6)),
    host_token = md5(id::text || random()::text);

ALTER TABLE party
    ALTER COLUMN join_code SET NOT NULL,
    ALTER COLUMN host_token SET NOT NULL;

CREATE UNIQUE INDEX uq_party_join_code ON party (join_code);
CREATE UNIQUE INDEX uq_party_host_token ON party (host_token);

ALTER TABLE party_member
    ADD COLUMN member_token VARCHAR(64);

UPDATE party_member
SET member_token = md5(id::text || random()::text);

ALTER TABLE party_member
    ALTER COLUMN party_id SET NOT NULL,
    ALTER COLUMN member_token SET NOT NULL;

CREATE UNIQUE INDEX uq_party_member_token ON party_member (member_token);
CREATE UNIQUE INDEX uq_party_member_normalized_name
    ON party_member (party_id, lower(btrim(member_name)));

CREATE UNIQUE INDEX uq_vote_party_member
    ON vote (party_id, member_id);

CREATE UNIQUE INDEX uq_suggestion_party_normalized_name
    ON suggestion (party_id, lower(btrim(name)));
