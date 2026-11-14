package ticketing.account.account.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import ticketing.account.account.application.result.AuthResult;
import ticketing.account.account.application.result.KakaoLoginResult;

public interface KakaoLoginResponse {
	
	@JsonInclude(JsonInclude.Include.NON_NULL)
	record Login(
			String result,
			String accessToken,
			long expiresIn,
			String refreshToken,
			TokenResponse.AccountResponse account
	) implements KakaoLoginResponse {}
	
	record SignupRequired(
			String result,
			String signupToken,
			String kakaoEmail
	) implements KakaoLoginResponse {}
	
	static KakaoLoginResponse of(KakaoLoginResult result, boolean includeRefreshToken) {
		if (result.status() == KakaoLoginResult.Status.SIGNUP_REQUIRED) {
			return new SignupRequired(
					result.status().name(),
					result.signupToken(),
					result.kakaoEmail()
			);
		}
		
		AuthResult auth = result.auth();
		AuthResult.AccountSummary account = auth.account();
		
		String refreshToken = includeRefreshToken
				? auth.refreshToken().refreshToken()
				: null;
		
		TokenResponse.AccountResponse accountResponse = new TokenResponse.AccountResponse(
				account.email(),
				account.nickname(),
				account.tag(),
				account.status()
		);
		
		return new Login(
				result.status().name(),
				auth.accessToken().accessToken(),
				auth.accessToken().expiresIn(),
				refreshToken,
				accountResponse
		);
	}
}
