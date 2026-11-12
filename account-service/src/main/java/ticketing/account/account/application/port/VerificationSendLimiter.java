package ticketing.account.account.application.port;

import ticketing.account.account.domain.model.Email;

public interface VerificationSendLimiter {

	// 재발송 한도를 넘으면 RateLimitExceededException
	void acquire(Email email, String clientIp);
}
