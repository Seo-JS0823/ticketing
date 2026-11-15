package ticketing.account.auth.application.result;

public record AccessTokenResult(
		String accessToken,
		long expiresIn
) {

}
