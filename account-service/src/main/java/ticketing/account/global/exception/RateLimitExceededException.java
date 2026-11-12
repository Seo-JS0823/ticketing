package ticketing.account.global.exception;

import java.time.Duration;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends DomainException {

	private final long retryAfterSeconds;
	
	public RateLimitExceededException(Duration retryAfter) {
		super(CommonErrorCode.TOO_MANY_REQUESTS);
		
		long millis = Math.max(retryAfter.toMillis(), 1);
		this.retryAfterSeconds = (millis + 999) / 1000;
	}
}
