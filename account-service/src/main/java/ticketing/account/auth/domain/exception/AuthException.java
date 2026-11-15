package ticketing.account.auth.domain.exception;

import ticketing.account.global.exception.DomainErrorCode;
import ticketing.account.global.exception.DomainException;

public class AuthException extends DomainException {

	public AuthException(DomainErrorCode errorCode) {
		super(errorCode);
	}
}
