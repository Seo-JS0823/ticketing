package ticketing.account.account.domain.exception;

import ticketing.account.global.exception.DomainErrorCode;
import ticketing.account.global.exception.DomainException;

public class AccountException extends DomainException {

	public AccountException(DomainErrorCode errorCode) {
		super(errorCode);
	}
}
