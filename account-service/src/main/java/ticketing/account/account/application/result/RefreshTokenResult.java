package ticketing.account.account.application.result;

import java.util.UUID;

public record RefreshTokenResult(
		UUID accountId,
		String refreshToken,
		long expiresIn
) {

}
