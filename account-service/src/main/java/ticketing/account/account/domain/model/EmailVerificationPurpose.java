package ticketing.account.account.domain.model;

public enum EmailVerificationPurpose {
	SIGN_UP,
	CHANGE_EMAIL,
	PASSWORD_RESET,
	SENSITIVE_ACTION,
	ACCOUNT_RECOVERY,
	RECOVERY_PASSWORD_RESET
}
