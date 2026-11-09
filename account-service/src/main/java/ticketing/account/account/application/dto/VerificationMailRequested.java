package ticketing.account.account.application.dto;

import java.util.UUID;

import ticketing.account.account.application.VerificationMailType;
import ticketing.account.account.domain.model.EmailVerificationPurpose;

public record VerificationMailRequested(
		UUID verificationId,
		String to,
		EmailVerificationPurpose purpose,
		VerificationMailType type,
		String code
) {

}
