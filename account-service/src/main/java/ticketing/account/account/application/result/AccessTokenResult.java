package ticketing.account.account.application.result;

public record AccessTokenResult(
		String accessToken,
		long expiresIn
) {

}
