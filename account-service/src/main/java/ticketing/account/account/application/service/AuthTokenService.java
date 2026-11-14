package ticketing.account.account.application.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.port.AccessTokenIssuer;
import ticketing.account.account.application.result.AccessTokenResult;
import ticketing.account.account.application.result.AuthResult;
import ticketing.account.account.application.result.RefreshTokenResult;
import ticketing.account.account.application.result.AuthResult.AccountSummary;
import ticketing.account.account.domain.model.Account;

@Service
@RequiredArgsConstructor
public class AuthTokenService {

	private final AccessTokenIssuer accessTokenIssuer;
	
	private final RefreshTokenService refreshTokenService;
	
	public AuthResult issue(Account account) {
		AccessTokenResult accessToken = accessTokenIssuer.issue(
				account.getId(),
				account.getRole(),
				account.getStatus()
		);
		
		RefreshTokenResult refreshToken = refreshTokenService.issue(
				account.getId()
		);
		
		return new AuthResult(accessToken, refreshToken, AccountSummary.from(account));
	}
}
