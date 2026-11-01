package ticketing.account.account.domain.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.account.global.exception.DomainErrorCode;

@Getter
@RequiredArgsConstructor
public enum AccountErrorCode implements DomainErrorCode {
	
	;
	private final HttpStatus status;
	
	private final String message;
	
	@Override
	public String getCode() {
		return this.name();
	}
}
