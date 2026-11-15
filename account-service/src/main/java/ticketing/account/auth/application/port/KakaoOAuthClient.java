package ticketing.account.auth.application.port;

import ticketing.account.auth.application.result.KakaoUser;

public interface KakaoOAuthClient {

	KakaoUser fetchUserByAuthorizationCode(
			String authorizationCode,
			String redirectUri
	);
	
	KakaoUser fetchUserByAccessToken(
			String kakaoAccessToken
	);
}
