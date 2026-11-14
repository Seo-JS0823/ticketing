package ticketing.account.account.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.application.port.KakaoOAuthClient;
import ticketing.account.account.application.result.KakaoUser;
import ticketing.account.account.domain.exception.AuthErrorCode;
import ticketing.account.account.domain.exception.AuthException;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.account.infra.persistence.JpaSocialSignupTokenRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class KakaoLoginApiTest {

	private static final String KAKAO_USER_ID = "1234567890";
	
	private static final String REDIRECT_URI = "http://localhost:3000/oauth/kakao";
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private AccountRepository accountRepository;
	
	@Autowired
	private JpaSocialSignupTokenRepository jpaSocialSignupTokenRepository;
	
	// 실제 카카오를 부르지 않도록 포트를 가짜로 바꾼다.
	@MockitoBean
	private KakaoOAuthClient kakaoOAuthClient;
	
	@Test
	@DisplayName("웹: 카카오가 연결된 계정이면 LOGIN, Refresh Token은 쿠키로 전달")
	void kakaoLoginAPI_test1() throws Exception {
		saveKakaoAccount("linked@example.com", "LINKED01");
		when(kakaoOAuthClient.fetchUserByAuthorizationCode("auth-code", REDIRECT_URI))
				.thenReturn(new KakaoUser(KAKAO_USER_ID, "kakao@example.com"));
		
		webLogin("auth-code", REDIRECT_URI)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.result").value("LOGIN"))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.expiresIn").value(900))
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andExpect(jsonPath("$.data.account.email").value("linked@example.com"))
				.andExpect(jsonPath("$.data.signupToken").doesNotExist())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
						containsString("refresh_token="),
						containsString("Path=/api/v1/auth"),
						containsString("HttpOnly")
				)));
	}
	
	@Test
	@DisplayName("앱: 카카오가 연결된 계정이면 LOGIN, Refresh Token은 바디로 전달하고 쿠키는 없음")
	void kakaoLoginAPI_test2() throws Exception {
		saveKakaoAccount("app@example.com", "APPUSER1");
		when(kakaoOAuthClient.fetchUserByAccessToken("app-access-token"))
				.thenReturn(new KakaoUser(KAKAO_USER_ID, null));
		
		appLogin("app-access-token")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.result").value("LOGIN"))
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}
	
	@Test
	@DisplayName("연결된 계정이 없으면 SIGNUP_REQUIRED와 signupToken, 카카오 이메일 전달, 토큰과 쿠키는 없음")
	void kakaoLoginAPI_test3() throws Exception {
		when(kakaoOAuthClient.fetchUserByAuthorizationCode("auth-code", REDIRECT_URI))
				.thenReturn(new KakaoUser(KAKAO_USER_ID, "kakao@example.com"));
		
		webLogin("auth-code", REDIRECT_URI)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.result").value("SIGNUP_REQUIRED"))
				.andExpect(jsonPath("$.data.signupToken").isNotEmpty())
				.andExpect(jsonPath("$.data.kakaoEmail").value("kakao@example.com"))
				.andExpect(jsonPath("$.data.accessToken").doesNotExist())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		
		assertThat(jpaSocialSignupTokenRepository.findAll()).hasSize(1);
		assertThat(jpaSocialSignupTokenRepository.findAll().get(0).getProviderUserId()).isEqualTo(KAKAO_USER_ID);
	}
	
	@Test
	@DisplayName("카카오 이메일을 받지 못하면 kakaoEmail은 null로 응답")
	void kakaoLoginAPI_test4() throws Exception {
		when(kakaoOAuthClient.fetchUserByAccessToken("app-access-token"))
				.thenReturn(new KakaoUser(KAKAO_USER_ID, null));
		
		appLogin("app-access-token")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.result").value("SIGNUP_REQUIRED"))
				.andExpect(jsonPath("$.data", hasKey("kakaoEmail")))
				.andExpect(jsonPath("$.data.kakaoEmail").value(nullValue()));
	}
	
	@Test
	@DisplayName("허용되지 않은 redirectUri면 카카오를 호출하지 않고 INVALID_SOCIAL_CREDENTIAL")
	void kakaoLoginAPI_test5() throws Exception {
		webLogin("auth-code", "https://evil.example.com/oauth/kakao")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_SOCIAL_CREDENTIAL"));
		
		verify(kakaoOAuthClient, never()).fetchUserByAuthorizationCode(anyString(), anyString());
	}
	
	@Test
	@DisplayName("카카오 인증에 실패하면 INVALID_SOCIAL_CREDENTIAL")
	void kakaoLoginAPI_test6() throws Exception {
		when(kakaoOAuthClient.fetchUserByAccessToken("expired-token"))
				.thenThrow(new AuthException(AuthErrorCode.INVALID_SOCIAL_CREDENTIAL));
		
		appLogin("expired-token")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_SOCIAL_CREDENTIAL"));
	}
	
	@Test
	@DisplayName("인가 코드와 카카오 토큰을 둘 다 보내거나 둘 다 없으면 INVALID_REQUEST")
	void kakaoLoginAPI_test7() throws Exception {
		mockMvc.perform(loginRequest(
						"""
						{"authorizationCode": "auth-code", "redirectUri": "%s", "kakaoAccessToken": "app-access-token"}
						""".formatted(REDIRECT_URI)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		
		mockMvc.perform(loginRequest("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
	
	// X-Client-Type 없이 요청 (WEB)
	private ResultActions webLogin(String authorizationCode, String redirectUri) throws Exception {
		return mockMvc.perform(loginRequest(
				"""
				{"authorizationCode": "%s", "redirectUri": "%s"}
				""".formatted(authorizationCode, redirectUri)));
	}
	
	private ResultActions appLogin(String kakaoAccessToken) throws Exception {
		return mockMvc.perform(loginRequest(
				"""
				{"kakaoAccessToken": "%s"}
				""".formatted(kakaoAccessToken))
				.header("X-Client-Type", "APP"));
	}
	
	private MockHttpServletRequestBuilder loginRequest(String body) {
		return post("/api/v1/auth/kakao")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}
	
	// 카카오 계정이 연결된 활성 계정
	private Account saveKakaoAccount(String email, String tag) {
		Instant now = Instant.now();
		
		Account account = Account.register(
				PublicIdentifier.of("티켓러", tag),
				Email.of(email),
				now,
				now
		);
		account.connectSocialAccount(SocialProvider.KAKAO, KAKAO_USER_ID, now);
		
		return accountRepository.save(account);
	}
}