package ticketing.account.account.presentation.request;

import jakarta.validation.constraints.NotNull;
import ticketing.account.account.domain.model.EmailVerificationPurpose;

public record EmailVerificationCreateRequest(
		String email,
		@NotNull EmailVerificationPurpose purpose
) {

}
