-- 이메일 소문자 정규화
-- 적용 전 중복 확인
UPDATE account.accounts
SET email = lower(email)
WHERE email <> lower(email);

-- 공개 식별자: 대소문자 무시 유니크 인덱스로 변경
ALTER TABLE account.accounts
DROP CONSTRAINT uk_accounts_nickname_tag;

CREATE UNIQUE INDEX uk_accounts_nickname_tag_lower
ON account.accounts (lower(nickname), lower(tag));

-- 소셜 연결: 수정 시각 및 Soft Delete
ALTER TABLE account.social_accounts
ADD COLUMN updated_at TIMESTAMPTZ,
ADD COLUMN deleted_at TIMESTAMPTZ;

UPDATE account.social_accounts
SET updated_at = linked_at;

ALTER TABLE account.social_accounts
ALTER COLUMN updated_at SET NOT NULL;

-- 소셜 연결: 활성 연결만 유일하게
ALTER TABLE account.social_accounts
DROP CONSTRAINT uk_social_accounts_provider_user,
DROP CONSTRAINT uk_social_accounts_account_provider;

CREATE UNIQUE INDEX uk_social_accounts_provider_user_active
ON account.social_accounts (provider, provider_user_id)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_social_accounts_account_provider_active
ON account.social_accounts (account_id, provider)
WHERE deleted_at IS NULL;