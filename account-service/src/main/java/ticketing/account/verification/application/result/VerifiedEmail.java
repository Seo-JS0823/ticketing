package ticketing.account.verification.application.result;

import java.time.Instant;
import java.util.UUID;

import ticketing.account.account.domain.model.Email;

public record VerifiedEmail(
		Email email,
		UUID accountId,
		Instant verifiedAt
) {

}
