package ticketing.account.account.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.port.KakaoOAuthClient;
import ticketing.account.account.application.properties.KakaoProperties;
import ticketing.account.account.application.result.KakaoLoginResult;
import ticketing.account.account.application.result.KakaoUser;
import ticketing.account.account.domain.exception.AuthErrorCode;
import ticketing.account.account.domain.exception.AuthException;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.domain.repository.AccountRepository;

@Service
@RequiredArgsConstructor
public class KakaoLoginService {

	private final KakaoOAuthClient kakaoOAuthClient;
	
	private final KakaoProperties kakaoProperties;
	
	private final AccountRepository accountRepository;
	
	private final SocialSignupTokenService socialSignupTokenService;
	
	private final AuthTokenService authTokenService;
	
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
		
		return KakaoLoginResult.login(authTokenService.issue(account));
	}
}
