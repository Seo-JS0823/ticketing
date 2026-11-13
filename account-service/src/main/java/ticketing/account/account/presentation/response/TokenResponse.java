package ticketing.account.account.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import ticketing.account.account.application.result.AuthResult;
import ticketing.account.account.domain.model.AccountStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(
		String accessToken,
		long expiresIn,
		String refreshToken,
		AccountResponse account
) {
	
	public record AccountResponse(
			String email,
			String nickname,
			String tag,
			AccountStatus status
	) {}
	
	public static TokenResponse of(AuthResult result, boolean includeRefreshToken) {
		AuthResult.AccountSummary account = result.account();
		
		String refreshToken = includeRefreshToken
				? result.refreshToken().refreshToken()
				: null; 
		
		AccountResponse accountResponse = new AccountResponse(
				account.email(),
				account.nickname(),
				account.tag(),
				account.status()
		);
		
		return new TokenResponse(
				result.accessToken().accessToken(),
				result.accessToken().expiresIn(),
				refreshToken,
				accountResponse
		);
	}
}
