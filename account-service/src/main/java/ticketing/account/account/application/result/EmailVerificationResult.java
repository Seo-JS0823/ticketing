package ticketing.account.account.application.result;

import java.time.Instant;
import java.util.UUID;

public record EmailVerificationResult(
		UUID verificationId,
		Instant expiresAt
) {

}
