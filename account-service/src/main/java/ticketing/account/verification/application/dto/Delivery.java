package ticketing.account.verification.application.dto;

import java.util.UUID;

import ticketing.account.account.domain.model.Email;

public record Delivery(
		Email email,
		UUID accountId,
		VerificationMailType mailType
) {

}
