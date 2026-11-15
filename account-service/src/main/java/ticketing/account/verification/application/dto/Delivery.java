package ticketing.account.verification.application.dto;

import java.util.UUID;

public record Delivery(
		UUID accountId,
		VerificationMailType mailType
) {

}
