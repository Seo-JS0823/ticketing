package ticketing.account.account.application.result;

public record KakaoLoginResult(
		Status status,
		AuthResult auth,
		String signupToken,
		String kakaoEmail
) {
	
	public enum Status {
		LOGIN, SIGNUP_REQUIRED
	}
	
	public static KakaoLoginResult login(AuthResult auth) {
		return new KakaoLoginResult(Status.LOGIN, auth, null, null);
	}
	
	public static KakaoLoginResult signupRequired(String signupToken, String kakaoEmail) {
		return new KakaoLoginResult(Status.SIGNUP_REQUIRED, null, signupToken, kakaoEmail);
	}
}
