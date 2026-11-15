package ticketing.account.auth.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.auth.application.port.KakaoOAuthClient;
import ticketing.account.auth.application.properties.KakaoProperties;
import ticketing.account.auth.application.result.KakaoLoginResult;
import ticketing.account.auth.application.result.KakaoUser;
import ticketing.account.auth.domain.exception.AuthErrorCode;
import ticketing.account.auth.domain.exception.AuthException;

@Service
@RequiredArgsConstructor
public class KakaoLoginService {

	private final KakaoOAuthClient kakaoOAuthClient;
	
	private final KakaoProperties kakaoProperties;
	
	private final AccountRepository accountRepository;
	
	private final SocialSignupTokenService socialSignupTokenService;
	
	private final TokenService tokenService;
	
	public KakaoLoginResult loginWithAuthorizationCode(String authorizationCode, String redirectUri) {
		if (!kakaoProperties.isAllowedRedirectUri(redirectUri)) {
			throw new AuthException(AuthErrorCode.INVALID_SOCIAL_CREDENTIAL);
		}
		
		return login(kakaoOAuthClient.fetchUserByAuthorizationCode(authorizationCode, redirectUri));
	}
	
	public KakaoLoginResult loginWithAccessToken(String kakaoAccessToken) {
		return login(kakaoOAuthClient.fetchUserByAccessToken(kakaoAccessToken));
	}
	
	private KakaoLoginResult login(KakaoUser kakaoUser) {
		Optional<Account> linkedAccount = accountRepository.findBySocialAccount(
				SocialProvider.KAKAO,
				kakaoUser.id()
		);
		
		if (linkedAccount.isEmpty()) {
			String signupToken = socialSignupTokenService.issue(
					SocialProvider.KAKAO,
					kakaoUser.id(),
					kakaoUser.email()
			);
			
			return KakaoLoginResult.signupRequired(signupToken, kakaoUser.email());
		}
		
		Account account = linkedAccount.get();
		
		if (account.getStatus() == AccountStatus.WITHDRAWN) {
			throw new AuthException(AuthErrorCode.ACCOUNT_WITHDRAWN);
		}
		
		return KakaoLoginResult.login(tokenService.issue(account));
	}
}
