package ticketing.account.account.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.application.port.PasswordHasher;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.auth.application.port.AccessTokenIssuer;
import ticketing.account.auth.application.service.TokenService;
import ticketing.account.global.crypto.TokenCrypto;
import ticketing.account.verification.domain.model.EmailVerification;
import ticketing.account.verification.domain.model.EmailVerificationPurpose;
import ticketing.account.verification.infra.persistence.JpaEmailVerificationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class WithdrawalApiTest {

	private static final String WITHDRAWAL_URL = "/api/v1/accounts/me/withdrawal";
	
	private static final String REFRESH_URL = "/api/v1/auth/refresh";
	
	private static final String EMAIL = "withdraw@example.com";
	
	private static final String PASSWORD = "password1!";
	
	private static final String WRONG_PASSWORD = "wrong123!";
	
	private static final String TOKEN = "sensitive-action-token";
	
	private static final String CODE = "123456";
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private AccountRepository accountRepository;
	
	@Autowired
	private PasswordHasher passwordHasher;
	
	@Autowired
	private AccessTokenIssuer accessTokenIssuer;
	
	@Autowired
	private TokenService tokenService;
	
	@Autowired
	private TokenCrypto tokenCrypto;
	
	@Autowired
	private JpaEmailVerificationRepository jpaEmailVerificationRepository;
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	@Autowired
	private RedisConnectionFactory redisConnectionFactory;
	
	// 로그인 실패 카운터가 다음 테스트에 남지 않게 한다.
	@BeforeEach
	void clearRedis() {
		try (RedisConnection connection = redisConnectionFactory.getConnection()) {
			connection.serverCommands().flushDb();
		}
	}
	
	@Test
	@DisplayName("비밀번호 계정이 맞는 비밀번호로 탈퇴하면 200, WITHDRAWN과 복구 기한(탈퇴 시각 + 30일), 비밀번호는 유지")
	void withdrawalAPI_test1() throws Exception {
		Account account = savePasswordAccount();
		
		withdraw(account, PASSWORD, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
				.andExpect(jsonPath("$.data.recoveryExpiresAt").exists());
		
		Account withdrawn = accountRepository.findById(account.getId()).orElseThrow();
		
		assertThat(withdrawn.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
		assertThat(withdrawn.getWithdrawnAt()).isNotNull();
		assertThat(withdrawn.getRecoveryExpiresAt())
				.isEqualTo(withdrawn.getWithdrawnAt().plus(Duration.ofDays(30)));
		assertThat(countCredentials(account.getId())).isEqualTo(1);
	}
	
	@Test
	@DisplayName("탈퇴하면 그 계정의 모든 세션을 폐기")
	void withdrawalAPI_test2() throws Exception {
		Account account = savePasswordAccount();
		String web = tokenService.issueRefreshToken(account.getId()).refreshToken();
		String app = tokenService.issueRefreshToken(account.getId()).refreshToken();
		
		withdraw(account, PASSWORD, null)
				.andExpect(status().isOk());
		
		refreshWithBody(web)
				.andExpect(status().isUnauthorized());
		
		refreshWithBody(app)
				.andExpect(status().isUnauthorized());
	}
	
	@Test
	@DisplayName("현재 비밀번호가 틀리면 401 INVALID_CREDENTIALS, 탈퇴되지 않음")
	void withdrawalAPI_test3() throws Exception {
		Account account = savePasswordAccount();
		
		withdraw(account, WRONG_PASSWORD, null)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		
		assertStatus(account, AccountStatus.ACTIVE);
	}
	
	@Test
	@DisplayName("비밀번호 계정은 인증 완료 토큰만 보내면 401 INVALID_CREDENTIALS, 토큰은 소모되지 않음 (DEC-01)")
	void withdrawalAPI_test4() throws Exception {
		Account account = savePasswordAccount();
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.SENSITIVE_ACTION);
		
		withdraw(account, null, TOKEN)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		
		assertStatus(account, AccountStatus.ACTIVE);
		assertTokenNotConsumed();
	}
	
	@Test
	@DisplayName("현재 비밀번호를 5번 틀리면 맞는 비밀번호도 429 (DEC-02)")
	void withdrawalAPI_test5() throws Exception {
		Account account = savePasswordAccount();
		
		for (int i = 0; i < 5; i++) {
			withdraw(account, WRONG_PASSWORD, null)
					.andExpect(status().isUnauthorized());
		}
		
		withdraw(account, PASSWORD, null)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
				.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
		
		assertStatus(account, AccountStatus.ACTIVE);
	}
	
	@Test
	@DisplayName("소셜 전용 계정은 SENSITIVE_ACTION 토큰으로 탈퇴하고 소셜 연결은 유지")
	void withdrawalAPI_test6() throws Exception {
		Account account = saveSocialAccount(EMAIL, "SOCIAL01");
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.SENSITIVE_ACTION);
		
		withdraw(account, null, TOKEN)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
		
		assertStatus(account, AccountStatus.WITHDRAWN);
		assertThat(countActiveSocialAccounts(account.getId())).isEqualTo(1);
	}
	
	@Test
	@DisplayName("다른 계정에 귀속된 토큰은 400 INVALID_VERIFICATION_TOKEN, 탈퇴되지 않고 토큰도 소모되지 않음")
	void withdrawalAPI_test7() throws Exception {
		Account account = saveSocialAccount(EMAIL, "SOCIAL01");
		Account other = saveSocialAccount("other@example.com", "OTHER01");
		saveVerified(other.getId(), "other@example.com", EmailVerificationPurpose.SENSITIVE_ACTION);
		
		withdraw(account, null, TOKEN)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
		
		assertStatus(account, AccountStatus.ACTIVE);
		assertTokenNotConsumed();
	}
	
	@Test
	@DisplayName("다른 목적(PASSWORD_RESET)의 토큰은 400 INVALID_VERIFICATION_TOKEN")
	void withdrawalAPI_test8() throws Exception {
		Account account = saveSocialAccount(EMAIL, "SOCIAL01");
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.PASSWORD_RESET);
		
		withdraw(account, null, TOKEN)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
		
		assertStatus(account, AccountStatus.ACTIVE);
	}
	
	@Test
	@DisplayName("소셜 전용 계정이 토큰 없이 요청하면 400 INVALID_VERIFICATION_TOKEN")
	void withdrawalAPI_test9() throws Exception {
		Account account = saveSocialAccount(EMAIL, "SOCIAL01");
		
		withdraw(account, PASSWORD, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
		
		assertStatus(account, AccountStatus.ACTIVE);
	}
	
	@Test
	@DisplayName("정지(SUSPENDED) 계정도 탈퇴할 수 있음")
	void withdrawalAPI_test10() throws Exception {
		Account account = savePasswordAccount();
		
		jdbcTemplate.update(
				"UPDATE account.accounts SET status = 'SUSPENDED' WHERE id = ?",
				account.getId()
		);
		
		withdraw(account, PASSWORD, null)
				.andExpect(status().isOk());
		
		assertStatus(account, AccountStatus.WITHDRAWN);
	}
	
	@Test
	@DisplayName("이미 탈퇴한 계정이 남은 Access Token으로 다시 요청하면 409 ACCOUNT_NOT_ACTIVE")
	void withdrawalAPI_test11() throws Exception {
		Account account = savePasswordAccount();
		String accessToken = accessToken(account);
		
		withdraw(accessToken, PASSWORD, null)
				.andExpect(status().isOk());
		
		withdraw(accessToken, PASSWORD, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
	}
	
	@Test
	@DisplayName("Access Token이 없으면 401 UNAUTHORIZED")
	void withdrawalAPI_test12() throws Exception {
		mockMvc.perform(post(WITHDRAWAL_URL)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(PASSWORD, null)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
	
	private ResultActions withdraw(Account account, String currentPassword, String verificationToken) throws Exception {
		return withdraw(accessToken(account), currentPassword, verificationToken);
	}
	
	private ResultActions withdraw(String accessToken, String currentPassword, String verificationToken) throws Exception {
		return mockMvc.perform(post(WITHDRAWAL_URL)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(currentPassword, verificationToken)));
	}
	
	// null은 JSON null로 보낸다
	private String body(String currentPassword, String verificationToken) {
		return """
				{"currentPassword": %s, "verificationToken": %s}
				""".formatted(jsonValue(currentPassword), jsonValue(verificationToken));
	}
	
	private String jsonValue(String value) {
		return value == null
				? "null"
				: "\"" + value + "\"";
	}
	
	private ResultActions refreshWithBody(String refreshToken) throws Exception {
		return mockMvc.perform(post(REFRESH_URL)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"refreshToken": "%s"}
						""".formatted(refreshToken)
				));
	}
	
	// 실제 발급기로 만든 Access Token
	private String accessToken(Account account) {
		return accessTokenIssuer.issue(
				account.getId(),
				account.getRole(),
				account.getStatus()
		).accessToken();
	}
	
	private void assertStatus(Account account, AccountStatus expected) {
		assertThat(accountRepository.findById(account.getId()).orElseThrow().getStatus())
				.isEqualTo(expected);
	}
	
	// 실패 후에도 인증 완료 토큰이 사용 처리되지 않았는지 확인
	private void assertTokenNotConsumed() {
		assertThat(jpaEmailVerificationRepository.findAll())
				.singleElement()
				.satisfies(verification -> assertThat(verification.getConsumedAt()).isNull());
	}
	
	private int countCredentials(UUID accountId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM account.account_credentials WHERE account_id = ?",
				Integer.class,
				accountId
		);
	}
	
	private int countActiveSocialAccounts(UUID accountId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM account.social_accounts WHERE account_id = ? AND deleted_at IS NULL",
				Integer.class,
				accountId
		);
	}
	
	private Account savePasswordAccount() {
		Account account = newAccount(EMAIL, "PASS01");
		
		account.registerPassword(passwordHasher.hash(PASSWORD), Instant.now());
		
		return accountRepository.save(account);
	}
	
	// 비밀번호 없이 카카오만 연결된 계정
	private Account saveSocialAccount(String email, String tag) {
		Account account = newAccount(email, tag);
		
		account.connectSocialAccount(SocialProvider.KAKAO, "kakao-" + tag, Instant.now());
		
		return accountRepository.save(account);
	}
	
	private Account newAccount(String email, String tag) {
		Instant now = Instant.now();
		
		return Account.register(
				PublicIdentifier.of("탈퇴회원", tag),
				Email.of(email),
				now,
				now
		);
	}
	
	// 코드 검증까지 끝난 인증 요청 저장 (대상 계정 포함)
	private void saveVerified(UUID accountId, String email, EmailVerificationPurpose purpose) {
		Instant verifiedAt = Instant.now();
		
		EmailVerification verification = EmailVerification.request(
				accountId,
				Email.of(email),
				purpose,
				tokenCrypto.hash(CODE),
				verifiedAt.minusSeconds(30)
		);
		
		verification.verify(tokenCrypto.hash(CODE), tokenCrypto.hash(TOKEN), verifiedAt);
		
		jpaEmailVerificationRepository.save(verification);
	}
}