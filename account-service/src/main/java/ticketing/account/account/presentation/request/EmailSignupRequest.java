package ticketing.account.account.presentation.request;

public record EmailSignupRequest(
		String password,
		String nickname,
		String verificationToken
) {

}
