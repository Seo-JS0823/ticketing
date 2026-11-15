package ticketing.account.verification.application.event;

import java.util.UUID;

import ticketing.account.verification.application.dto.VerificationMailType;
import ticketing.account.verification.domain.model.EmailVerificationPurpose;

public record VerificationMailRequested(
		UUID verificationId,
		String to,
		EmailVerificationPurpose purpose,
		VerificationMailType type,
		String code
) {

}
