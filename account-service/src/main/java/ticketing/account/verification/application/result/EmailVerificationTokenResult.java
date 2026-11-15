package ticketing.account.verification.application.result;

import java.time.Instant;

public record EmailVerificationTokenResult(
		String verificationToken,
		Instant expiresAt
) {

}
