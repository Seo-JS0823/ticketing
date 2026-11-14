package ticketing.account.account.presentation.request;

import org.springframework.util.StringUtils;

public record KakaoLoginRequest(
		String authorizationCode,
		String redirectUri,
		String kakaoAccessToken
) {

	public boolean isWebRequest() {
		return StringUtils.hasText(authorizationCode)
				&& !StringUtils.hasText(kakaoAccessToken);
	}
	
	public boolean isAppRequest() {
		return StringUtils.hasText(kakaoAccessToken)
				&& !StringUtils.hasText(authorizationCode);
	}
}
