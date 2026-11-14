package ticketing.account.account.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.application.service.SocialSignupTokenService;
import ticketing.account.account.application.util.VerificationCrypto;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.EmailVerification;
import ticketing.account.account.domain.model.EmailVerificationPurpose;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.domain.model.SocialSignupToken;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.account.infra.persistence.JpaEmailVerificationRepository;
import ticketing.account.account.infra.persistence.JpaSocialSignupTokenRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class KakaoSignupApiTest {

	private static final String CODE = "123456";
	
	private static final String KAKAO_USER_ID = "1234567890";
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private AccountRepository accountRepository;
	
	@Autowired
	private SocialSignupTokenService socialSignupTokenService;
	
	@Autowired
	private JpaSocialSignupTokenRepository jpaSocialSignupTokenRepository;
	
	@Autowired
	private JpaEmailVerificationRepository jpaEmailVerificationRepository;
	
	@Autowired
	private VerificationCrypto verificationCrypto;
	
	@Autowired
	private TransactionTemplate transactionTemplate;
	
	@Test
	@DisplayName("두 토큰으로 가입하면 201, 카카오가 연결된 비밀번호 없는 계정 생성, 두 토큰 모두 사용 처리")
	void kakaoSignupAPI_test1() throws Exception {
		String signupToken = issueSignupToken();
		EmailVerification verification = saveVerified("kakao-user@example.com", "verify-token");
		
		signup(signupToken, "verify-token", "카카오유저")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.account.email").value("kakao-user@example.com"))
				.andExpect(jsonPath("$.data.account.nickname").value("카카오유저"))
				.andExpect(jsonPath("$.data.account.status").value("ACTIVE"));
		
		Account linked = accountRepository.findBySocialAccount(SocialProvider.KAKAO, KAKAO_USER_ID).orElseThrow();
		
		// credential은 LAZY라 트랜잭션 안에서 읽는다.
		boolean hasPassword = transactionTemplate.execute(status -> accountRepository
				.findById(linked.getId())
				.orElseThrow()
				.getCredential() != null);
		
		assertThat(linked.getEmail().getEmail()).isEqualTo("kakao-user@example.com");
		assertThat(hasPassword).isFalse();
		assertThat(findSignupToken(signupToken).getConsumedAt()).isNotNull();
		assertThat(find(verification).getConsumedAt()).isNotNull();
	}
	
	@Test
	@DisplayName("웹은 Refresh Token을 쿠키로, 앱은 바디로 전달")
	void kakaoSignupAPI_test2() throws Exception {
		String webSignupToken = socialSignupTokenService.issue(SocialProvider.KAKAO, "1111111111", null);
		saveVerified("web@example.com", "web-token");
		
		signup(webSignupToken, "web-token", "웹유저")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")));
		
		String appSignupToken = socialSignupTokenService.issue(SocialProvider.KAKAO, "2222222222", null);
		saveVerified("app@example.com", "app-token");
		
		mockMvc.perform(signupRequest(appSignupToken, "app-token", "앱유저").header("X-Client-Type", "APP"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}
	
	@Test
	@DisplayName("같은 signupToken으로 두 번 가입 불가")
	void kakaoSignupAPI_test3() throws Exception {
		String signupToken = issueSignupToken();
		saveVerified("first@example.com", "first-token");
		saveVerified("second@example.com", "second-token");
		
		signup(signupToken, "first-token", "티켓러")
				.andExpect(status().isCreated());
		
		signup(signupToken, "second-token", "티켓러")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SIGNUP_TOKEN"));
	}
	
	@Test
	@DisplayName("잘못된 signupToken이면 INVALID_SIGNUP_TOKEN, 인증 완료 토큰은 소모되지 않음")
	void kakaoSignupAPI_test4() throws Exception {
		EmailVerification verification = saveVerified("user@example.com", "verify-token");
		
		signup("unknown-signup-token", "verify-token", "티켓러")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SIGNUP_TOKEN"));
		
		assertThat(find(verification).getConsumedAt()).isNull();
	}
	
	@Test
	@DisplayName("잘못된 인증 완료 토큰이면 INVALID_VERIFICATION_TOKEN, signupToken은 소모되지 않음")
	void kakaoSignupAPI_test5() throws Exception {
		String signupToken = issueSignupToken();
		
		signup(signupToken, "unknown-verify-token", "티켓러")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
		
		assertThat(findSignupToken(signupToken).getConsumedAt()).isNull();
	}
	
	@Test
	@DisplayName("이미 가입된 이메일이면 EMAIL_ALREADY_IN_USE, 두 토큰 모두 소모되지 않음")
	void kakaoSignupAPI_test6() throws Exception {
		saveAccount("dup@example.com", "DUP00001", null);
		String signupToken = issueSignupToken();
		EmailVerification verification = saveVerified("dup@example.com", "dup-token");
		
		signup(signupToken, "dup-token", "티켓러")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"));
		
		assertThat(findSignupToken(signupToken).getConsumedAt()).isNull();
		assertThat(find(verification).getConsumedAt()).isNull();
	}
	
	@Test
	@DisplayName("카카오 계정이 이미 다른 계정에 연결되어 있으면 SOCIAL_IDENTITY_IN_USE, 두 토큰 모두 소모되지 않음")
	void kakaoSignupAPI_test7() throws Exception {
		saveAccount("linked@example.com", "LINKED01", KAKAO_USER_ID);
		String signupToken = issueSignupToken();
		EmailVerification verification = saveVerified("new@example.com", "new-token");
		
		signup(signupToken, "new-token", "티켓러")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SOCIAL_IDENTITY_IN_USE"));
		
		assertThat(findSignupToken(signupToken).getConsumedAt()).isNull();
		assertThat(find(verification).getConsumedAt()).isNull();
	}
	
	@Test
	@DisplayName("닉네임 형식이 틀리면 INVALID_NICKNAME_PATTERN")
	void kakaoSignupAPI_test8() throws Exception {
		String signupToken = issueSignupToken();
		saveVerified("nick@example.com", "nick-token");
		
		signup(signupToken, "nick-token", "티")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_NICKNAME_PATTERN"));
	}
	
	// X-Client-Type 없이 요청 (WEB)
	private ResultActions signup(String signupToken, String verificationToken, String nickname) throws Exception {
		return mockMvc.perform(signupRequest(signupToken, verificationToken, nickname));
	}
	
	private MockHttpServletRequestBuilder signupRequest(String signupToken, String verificationToken, String nickname) {
		return post("/api/v1/accounts/kakao")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"signupToken": "%s", "verificationToken": "%s", "nickname": "%s"}
						""".formatted(signupToken, verificationToken, nickname)
				);
	}
	
	// 카카오 로그인에서 SIGNUP_REQUIRED를 받은 상황
	private String issueSignupToken() {
		return socialSignupTokenService.issue(SocialProvider.KAKAO, KAKAO_USER_ID, "kakao@example.com");
	}
	
	private SocialSignupToken findSignupToken(String signupToken) {
		String tokenHash = verificationCrypto.hash(signupToken);
		
		return jpaSocialSignupTokenRepository.findAll().stream()
				.filter(token -> token.getTokenHash().equals(tokenHash))
				.findFirst()
				.orElseThrow();
	}
	
	// 코드 검증까지 끝난 가입 목적 인증 요청 저장
	private EmailVerification saveVerified(String email, String token) {
		Instant verifiedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		
		EmailVerification verification = EmailVerification.request(
				null,
				Email.of(email),
				EmailVerificationPurpose.SIGN_UP,
				verificationCrypto.hash(CODE),
				verifiedAt.minusSeconds(30)
		);
		
		verification.verify(verificationCrypto.hash(CODE), verificationCrypto.hash(token), verifiedAt);
		
		return jpaEmailVerificationRepository.save(verification);
	}
	
	private EmailVerification find(EmailVerification verification) {
		return jpaEmailVerificationRepository.findById(verification.getId()).orElseThrow();
	}
	
	// kakaoUserId가 있으면 카카오를 연결한 계정으로 저장
	private Account saveAccount(String email, String tag, String kakaoUserId) {
		Instant now = Instant.now();
		
		Account account = Account.register(
				PublicIdentifier.of("기존회원", tag),
				Email.of(email),
				now,
				now
		);
		
		if (kakaoUserId != null) {
			account.connectSocialAccount(SocialProvider.KAKAO, kakaoUserId, now);
		}
		
		return accountRepository.save(account);
	}
}