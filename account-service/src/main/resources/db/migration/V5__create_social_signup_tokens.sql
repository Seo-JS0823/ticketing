CREATE TABLE account.social_signup_tokens (
  id               UUID         NOT NULL,
  provider         VARCHAR(30)  NOT NULL,
  provider_user_id VARCHAR(255) NOT NULL,
  provider_email   VARCHAR(320),
  token_hash       VARCHAR(64)  NOT NULL,
  expires_at       TIMESTAMPTZ  NOT NULL,
  consumed_at      TIMESTAMPTZ,
  created_at       TIMESTAMPTZ  NOT NULL,
  
  CONSTRAINT pk_social_signup_tokens PRIMARY KEY (id),
  CONSTRAINT uk_social_signup_tokens_token_hash UNIQUE (token_hash)
);