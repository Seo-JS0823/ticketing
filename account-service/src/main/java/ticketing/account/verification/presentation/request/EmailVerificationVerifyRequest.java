package ticketing.account.verification.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record EmailVerificationVerifyRequest(
		@NotBlank String code
) {

}
