-- A user is identified by their GitHub account, not by an email/password we store.
-- github_id (numeric, immutable) is the key rather than the login, because a GitHub
-- user can rename themselves and the login would then point at a different person.
CREATE TABLE users (
    id                    UUID           PRIMARY KEY,
    github_id             BIGINT         NOT NULL UNIQUE,
    login                 VARCHAR(255)   NOT NULL,
    name                  VARCHAR(255),
    avatar_url            VARCHAR(1024),
    -- AES-GCM ciphertext, base64. NEVER the raw token: this grants repo read/write
    -- on the user's GitHub account, so a leaked backup must not be enough to use it.
    encrypted_token       TEXT,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    last_login_at         TIMESTAMPTZ
);

CREATE INDEX idx_users_login ON users (login);

-- Ownership. Nullable because existing rows predate users and have no owner.
-- Queries filter by user_id, so those legacy rows simply become invisible rather
-- than leaking into someone's account - safer than guessing an owner for them.
ALTER TABLE repositories  ADD COLUMN user_id UUID REFERENCES users(id);
ALTER TABLE conversations ADD COLUMN user_id UUID REFERENCES users(id);

CREATE INDEX idx_repositories_user  ON repositories (user_id);
CREATE INDEX idx_conversations_user ON conversations (user_id);