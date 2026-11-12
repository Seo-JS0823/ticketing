-- 테스트 전 데이터 초기화
TRUNCATE TABLE
  account.email_verifications,
  account.social_accounts,
  account.account_credentials,
  account.accounts
CASCADE;