package ticketing.account.account.application.port;

import ticketing.account.account.application.result.KakaoUser;

public interface KakaoOAuthClient {

	KakaoUser fetchUserByAuthorizationCode(
			String authorizationCode,
			String redirectUri
	);
	
	KakaoUser fetchUserByAccessToken(
			String kakaoAccessToken
	);
}
