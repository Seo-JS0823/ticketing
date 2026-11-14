package ticketing.account.account.infra.kakao;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.port.KakaoOAuthClient;
import ticketing.account.account.application.properties.KakaoProperties;
import ticketing.account.account.application.result.KakaoUser;
import ticketing.account.account.domain.exception.AuthErrorCode;
import ticketing.account.account.domain.exception.AuthException;

@Component
@RequiredArgsConstructor
public class RestClientKakaoOAuthClient implements KakaoOAuthClient {

	private static final int TEMPORARY_ERROR_CODE = -1;
	
	private final RestClient kakaoRestClient;
	
	private final KakaoProperties properties;

	@Override
	public KakaoUser fetchUserByAuthorizationCode(String authorizationCode, String redirectUri) {
		requireText(authorizationCode);
		requireText(redirectUri);
		
		String accessToken = exchangeToken(authorizationCode, redirectUri);
		
		return fetchUser(accessToken);
	}

	@Override
	public KakaoUser fetchUserByAccessToken(String kakaoAccessToken) {
		requireText(kakaoAccessToken);
		
		verifyIssuedForThisApp(kakaoAccessToken);
		
		return fetchUser(kakaoAccessToken);
	}
	
	private String exchangeToken(String authorizationCode, String redirectUri) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		
		form.add("grant_type", "authorization_code");
		form.add("client_id", properties.clientId());
		form.add("redirect_uri", redirectUri);
		form.add("code", authorizationCode);
		
		if (StringUtils.hasText(properties.clientSecret())) {
			form.add("client_secret", properties.clientSecret());
		}
		
		KakaoApiResponses.Token token = call(() -> kakaoRestClient
				.post()
				.uri(properties.authBaseUrl() + "/oauth/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(KakaoApiResponses.Token.class));
		
		if (token == null || !StringUtils.hasText(token.accessToken())) {
			throw invalidCredential();
		}
		
		return token.accessToken();
	}
	
	private void verifyIssuedForThisApp(String kakaoAccessToken) {
		KakaoApiResponses.TokenInfo tokenInfo = call(() -> kakaoRestClient
				.get()
				.uri(properties.apiBaseUrl() + "/v1/user/access_token_info")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
				.retrieve()
				.body(KakaoApiResponses.TokenInfo.class));
		
		if (tokenInfo == null || !Objects.equals(tokenInfo.appId(), properties.appId())) {
			throw invalidCredential();
		}
	}
	
	private KakaoUser fetchUser(String kakaoAccessToken) {
		KakaoApiResponses.User user = call(() -> kakaoRestClient
				.get()
				.uri(properties.apiBaseUrl() + "/v2/user/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
				.retrieve()
				.body(KakaoApiResponses.User.class));
		
		if (user == null || user.id() == null) {
			throw invalidCredential();
		}
		
		return new KakaoUser(String.valueOf(user.id()), user.usableEmail());
	}
	
	private <T> T call(Supplier<T> request) {
		try {
			return request.get();
		} catch (HttpClientErrorException e) {
			if (isTemporaryError(e)) {
				throw e;
			}
			
			throw invalidCredential();
		}
	}
	
	private boolean isTemporaryError(HttpClientErrorException e) {
		try {
			KakaoApiResponses.ApiError error = e.getResponseBodyAs(KakaoApiResponses.ApiError.class);
			
			return error != null && Objects.equals(error.code(), TEMPORARY_ERROR_CODE);
		} catch (RuntimeException parseFailure) {
			return false;
		}
	}
	
	private static void requireText(String value) {
		if (!StringUtils.hasText(value)) {
			throw invalidCredential();
		}
	}
	
	private static AuthException invalidCredential() {
		return new AuthException(AuthErrorCode.INVALID_SOCIAL_CREDENTIAL);
	}
}
