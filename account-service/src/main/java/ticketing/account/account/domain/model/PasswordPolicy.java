package ticketing.account.account.domain.model;

import java.util.regex.Pattern;

import ticketing.account.account.domain.exception.AccountException;
import ticketing.account.account.domain.exception.AccountInvalidErrorCode;

public class PasswordPolicy {

	private static final Pattern PASSWORD_PATTERN = Pattern.compile(
			"^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[!@#$%^&*])[A-Za-z0-9!@#$%^&*]{8,20}$"
	);
	
	private PasswordPolicy() {}
	
	public static void validate(String rawPassword) {
		if (rawPassword == null || !PASSWORD_PATTERN.matcher(rawPassword).matches()) {
			throw new AccountException(AccountInvalidErrorCode.INVALID_PASSWORD_PATTERN);
		}
	}
}
