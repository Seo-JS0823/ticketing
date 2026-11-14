package ticketing.account.account.presentation.request;

public record KakaoSignupRequest(
		String signupToken,
		String verificationToken,
		String nickname
) {

}
