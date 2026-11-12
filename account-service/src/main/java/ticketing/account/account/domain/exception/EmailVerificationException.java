package ticketing.account.account.domain.exception;

import ticketing.account.global.exception.DomainErrorCode;
import ticketing.account.global.exception.DomainException;

public class EmailVerificationException extends DomainException {

	public EmailVerificationException(DomainErrorCode errorCode) {
		super(errorCode);
	}

}
