package ticketing.account.global.exception;

import org.springframework.http.HttpStatus;

public interface DomainErrorCode {
	String getCode();
	
	HttpStatus getStatus();
	
	String getMessage();
}
