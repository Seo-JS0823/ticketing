package ticketing.account.account.application;

import java.util.UUID;

public record Delivery(
		UUID accountId,
		VerificationMailType mailType
) {

}
