CREATE TABLE account.email_verifications (
  id                UUID          NOT NULL,
  account_id        UUID,
  email             VARCHAR(320)  NOT NULL,
  purpose           VARCHAR(30)   NOT NULL,
  code_hash         VARCHAR(64)   NOT NULL,
  status            VARCHAR(20)   NOT NULL,
  failed_attempts   INT           NOT NULL,
  expires_at        TIMESTAMPTZ   NOT NULL,
  verified_at       TIMESTAMPTZ,
  token_hash        VARCHAR(64),
  token_expires_at  TIMESTAMPTZ,
  consumed_at       TIMESTAMPTZ,
  created_at        TIMESTAMPTZ   NOT NULL,
  
  CONSTRAINT pk_email_verifications PRIMARY KEY (id),
  CONSTRAINT fk_email_verifications_account
    FOREIGN KEY (account_id) REFERENCES account.accounts (id)
);

-- verificationToken 조회
CREATE UNIQUE INDEX uk_email_verifications_token_hash
  ON account.email_verifications (token_hash)
  WHERE token_hash IS NOT NULL;