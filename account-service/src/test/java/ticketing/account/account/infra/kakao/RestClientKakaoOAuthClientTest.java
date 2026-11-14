package ticketing.account.account.infra.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import ticketing.account.account.application.properties.KakaoProperties;
import ticketing.account.account.application.result.KakaoUser;
import ticketing.account.account.domain.exception.AuthErrorCode;
import ticketing.account.account.domain.exception.AuthException;

public class RestClientKakaoOAuthClientTest {

	private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
	
	private static final String TOKEN_INFO_URL = "https://kapi.kakao.com/v1/user/access_token_info";
	
	private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";
	
	private static final String REDIRECT_URI = "http://localhost:3000/oauth/kakao";
	
	private MockRestServiceServer kakaoServer;
	
	private RestClientKakaoOAuthClient kakaoOAuthClient;
	
	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		kakaoServer = MockRestServiceServer.bindTo(builder).build();
		
		KakaoProperties properties = new KakaoProperties(
				"test-client-id",
				"test-client-secret",
				123456L,
				List.of(REDIRECT_URI),
				"https://kauth.kakao.com",
				"https://kapi.kakao.com",
				Duration.ofSeconds(3),
				Duration.ofSeconds(5)
		);
		
		kakaoOAuthClient = new RestClientKakaoOAuthClient(builder.build(), properties);
	}
	
	@Test
	@DisplayName("웹: 인가 코드로 토큰을 받고 사용자 정보를 조회")
	void kakaoOAuthClient_test1() {
		kakaoServer.expect(requestTo(TOKEN_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().formDataContains(Map.of(
						"grant_type", "authorization_code",
						"client_id", "test-client-id",
						"client_secret", "test-client-secret",
						"redirect_uri", REDIRECT_URI,
						"code", "auth-code"
				)))
				.andRespond(withSuccess(
						"""
						{"token_type": "bearer", "access_token": "kakao-access-token", "expires_in": 43199}
						""",
						MediaType.APPLICATION_JSON
				));
		
		expectUser("kakao-access-token", userJson(true, true));
		
		KakaoUser user = kakaoOAuthClient.fetchUserByAuthorizationCode("auth-code", REDIRECT_URI);
		
		assertThat(user.id()).isEqualTo("1234567890");
		assertThat(user.email()).isEqualTo("kakao@example.com");
		kakaoServer.verify();
	}
	
	@Test
	@DisplayName("앱: 우리 앱 토큰인지 확인한 뒤 사용자 정보를 조회")
	void kakaoOAuthClient_test2() {
		expectTokenInfo("app-access-token", 123456L);
		expectUser("app-access-token", userJson(true, true));
		
		KakaoUser user = kakaoOAuthClient.fetchUserByAccessToken("app-access-token");
		
		assertThat(user.id()).isEqualTo("1234567890");
		kakaoServer.verify();
	}
	
	@Test
	@DisplayName("이메일이 유효하지 않거나 인증되지 않았으면 이메일은 null")
	void kakaoOAuthClient_test3() {
		expectTokenInfo("app-access-token", 123456L);
		expectUser("app-access-token", userJson(true, false));
		
		assertThat(kakaoOAuthClient.fetchUserByAccessToken("app-access-token").email()).isNull();
	}
	
	@Test
	@DisplayName("이메일 동의를 하지 않아 kakao_account에 이메일이 없어도 조회 성공")
	void kakaoOAuthClient_test4() {
		expectTokenInfo("app-access-token", 123456L);
		expectUser("app-access-token",
				"""
				{"id": 1234567890, "kakao_account": {"email_needs_agreement": true}}
				""");
		
		KakaoUser user = kakaoOAuthClient.fetchUserByAccessToken("app-access-token");
		
		assertThat(user.id()).isEqualTo("1234567890");
		assertThat(user.email()).isNull();
	}
	
	@Test
	@DisplayName("잘못되었거나 이미 사용한 인가 코드면 INVALID_SOCIAL_CREDENTIAL")
	void kakaoOAuthClient_test5() {
		kakaoServer.expect(requestTo(TOKEN_URL))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST)
						.contentType(MediaType.APPLICATION_JSON)
						.body(
								"""
								{"error": "invalid_grant", "error_code": "KOE320"}
								"""
						));
		
		assertInvalidCredential(() -> kakaoOAuthClient.fetchUserByAuthorizationCode("used-code", REDIRECT_URI));
	}
	
	@Test
	@DisplayName("다른 앱에서 발급된 토큰이면 INVALID_SOCIAL_CREDENTIAL, 사용자 정보는 조회하지 않음")
	void kakaoOAuthClient_test6() {
		expectTokenInfo("other-app-token", 999999L);
		
		assertInvalidCredential(() -> kakaoOAuthClient.fetchUserByAccessToken("other-app-token"));
		kakaoServer.verify();
	}
	
	@Test
	@DisplayName("유효하지 않은 토큰(401)이면 INVALID_SOCIAL_CREDENTIAL")
	void kakaoOAuthClient_test7() {
		kakaoServer.expect(requestTo(TOKEN_INFO_URL))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED)
						.contentType(MediaType.APPLICATION_JSON)
						.body(
								"""
								{"msg": "this access token does not exist", "code": -401}
								"""
						));
		
		assertInvalidCredential(() -> kakaoOAuthClient.fetchUserByAccessToken("expired-token"));
	}
	
	@Test
	@DisplayName("카카오 서버 오류(5xx)는 INVALID_SOCIAL_CREDENTIAL로 바꾸지 않음")
	void kakaoOAuthClient_test8() {
		kakaoServer.expect(requestTo(TOKEN_INFO_URL))
				.andRespond(withServerError());
		
		assertThatThrownBy(() -> kakaoOAuthClient.fetchUserByAccessToken("app-access-token"))
				.isInstanceOf(HttpServerErrorException.class);
	}
	
	@Test
	@DisplayName("카카오 일시 오류(code -1)는 400이어도 INVALID_SOCIAL_CREDENTIAL로 바꾸지 않음")
	void kakaoOAuthClient_test9() {
		kakaoServer.expect(requestTo(TOKEN_INFO_URL))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST)
						.contentType(MediaType.APPLICATION_JSON)
						.body(
								"""
								{"msg": "internal error", "code": -1}
								"""
						));
		
		assertThatThrownBy(() -> kakaoOAuthClient.fetchUserByAccessToken("app-access-token"))
				.isInstanceOf(HttpClientErrorException.class);
	}
	
	@Test
	@DisplayName("빈 값이면 카카오를 호출하지 않고 INVALID_SOCIAL_CREDENTIAL")
	void kakaoOAuthClient_test10() {
		assertInvalidCredential(() -> kakaoOAuthClient.fetchUserByAuthorizationCode("", REDIRECT_URI));
		assertInvalidCredential(() -> kakaoOAuthClient.fetchUserByAuthorizationCode("auth-code", null));
		assertInvalidCredential(() -> kakaoOAuthClient.fetchUserByAccessToken(" "));
		kakaoServer.verify();
	}
	
	private void expectTokenInfo(String accessToken, long appId) {
		kakaoServer.expect(requestTo(TOKEN_INFO_URL))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andRespond(withSuccess(
						"""
						{"id": 1234567890, "expires_in": 7199, "app_id": %d}
						""".formatted(appId),
						MediaType.APPLICATION_JSON
				));
	}
	
	private void expectUser(String accessToken, String body) {
		kakaoServer.expect(requestTo(USER_URL))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}
	
	private String userJson(boolean emailValid, boolean emailVerified) {
		return """
				{
				  "id": 1234567890,
				  "kakao_account": {
				    "email": "kakao@example.com",
				    "is_email_valid": %s,
				    "is_email_verified": %s
				  }
				}
				""".formatted(emailValid, emailVerified);
	}
	
	private void assertInvalidCredential(ThrowingCallable callable) {
		assertThatThrownBy(callable)
				.isInstanceOf(AuthException.class)
				.extracting(e -> ((AuthException) e).getErrorCode())
				.isEqualTo(AuthErrorCode.INVALID_SOCIAL_CREDENTIAL);
	}
}