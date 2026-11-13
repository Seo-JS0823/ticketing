CREATE TABLE account.refresh_tokens (
  id            UUID        NOT NULL,
  session_id    UUID        NOT NULL,
  account_id    UUID        NOT NULL,
  token_hash    VARCHAR(64) NOT NULL,
  expires_at    TIMESTAMPTZ NOT NULL,
  used_at       TIMESTAMPTZ,
  revoked_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL,
  
  CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
  CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
  CONSTRAINT fk_refresh_tokens_account
    FOREIGN KEY (account_id) REFERENCES account.accounts (id)
);

CREATE INDEX idx_refresh_token_session_id
  ON account.refresh_tokens (session_id);
  
CREATE INDEX idx_refresh_tokens_account_id
  ON account.refresh_tokens (account_id);