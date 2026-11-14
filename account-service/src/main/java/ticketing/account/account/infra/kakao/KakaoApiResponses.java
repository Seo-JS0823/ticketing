package ticketing.account.account.infra.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


final class KakaoApiResponses {

	private KakaoApiResponses() {}
	
	// POST /oauth/token
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Token(
			@JsonProperty("access_token") String accessToken
	) {}
	
	// GET /v1/user/access_token_info
	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenInfo(
			Long id,
			@JsonProperty("app_id") Long appId
	) {}
	
	// GET /v2/user/me
	@JsonIgnoreProperties(ignoreUnknown = true)
	record User(
			Long id,
			@JsonProperty("kakao_account") Account kakaoAccount
	) {
		
		String usableEmail() {
			if (kakaoAccount == null || kakaoAccount.email() == null) {
				return null;
			}
			
			boolean usable = Boolean.TRUE.equals(kakaoAccount.isEmailValid())
					&& Boolean.TRUE.equals(kakaoAccount.isEmailVerified());
			
			return usable ? kakaoAccount.email() : null;
		}
	}
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Account(
			String email,
			@JsonProperty("is_email_valid") Boolean isEmailValid,
			@JsonProperty("is_email_verified") Boolean isEmailVerified
	) {}
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	record ApiError(
			Integer code,
			String msg
	) {}
}
