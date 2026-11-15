package ticketing.account.verification.presentation.request;

import jakarta.validation.constraints.NotNull;
import ticketing.account.verification.domain.model.EmailVerificationPurpose;

public record EmailVerificationCreateRequest(
		String email,
		@NotNull EmailVerificationPurpose purpose
) {

}
