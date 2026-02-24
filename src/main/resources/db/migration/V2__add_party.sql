CREATE TABLE party (
     id          BIGSERIAL PRIMARY KEY,
     name        VARCHAR(255) NOT NULL,
     description TEXT,
     created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
     updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE party_member (
    id          BIGSERIAL PRIMARY KEY,
    party_id    BIGINT REFERENCES party(id),
    member_name VARCHAR(255) NOT NULL,
    joined_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
)