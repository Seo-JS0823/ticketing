package ticketing.account.account.application.dto;

import java.util.UUID;

import ticketing.account.account.application.VerificationMailType;

public record Delivery(
		UUID accountId,
		VerificationMailType mailType
) {

}
