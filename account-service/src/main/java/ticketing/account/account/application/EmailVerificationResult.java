package ticketing.account.account.application;

import java.time.Instant;
import java.util.UUID;

public record EmailVerificationResult(
		UUID verificationId,
		Instant expiresAt
) {

}
