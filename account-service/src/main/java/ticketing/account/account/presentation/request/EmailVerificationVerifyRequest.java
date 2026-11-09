package ticketing.account.account.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record EmailVerificationVerifyRequest(
		@NotBlank String code
) {

}
