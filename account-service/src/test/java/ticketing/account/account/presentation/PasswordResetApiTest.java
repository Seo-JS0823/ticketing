package ticketing.account.account.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.application.port.PasswordHasher;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.repository.AccountRepository;
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
public class PasswordResetApiTest {

	private static final String RESET_URL = "/api/v1/auth/password-reset";
	
	private static final String LOGIN_URL = "/api/v1/auth/login";
	
	private static final String REFRESH_URL = "/api/v1/auth/refresh";
	
	private static final String EMAIL = "reset@example.com";
	
	private static final String OLD_PASSWORD = "oldpass1!";
	
	private static final String NEW_PASSWORD = "newpass1!";
	
	private static final String TOKEN = "password-reset-token";
	
	private static final String CODE = "123456";
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private AccountRepository accountRepository;
	
	@Autowired
	private PasswordHasher passwordHasher;
	
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
	@DisplayName("재설정 성공 시 200 SUCCESS, 새 비밀번호로 로그인되고 이전 비밀번호는 실패")
	void passwordResetAPI_test1() throws Exception {
		Account account = savePasswordAccount();
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.PASSWORD_RESET);
		
		reset(TOKEN, NEW_PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data").doesNotExist());
		
		login(NEW_PASSWORD)
				.andExpect(status().isOk());
		
		login(OLD_PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}
	
	@Test
	@DisplayName("소셜 전용 계정은 비밀번호를 새로 등록하고 이메일로 로그인 가능")
	void passwordResetAPI_test2() throws Exception {
		Account account = saveAccountWithoutPassword();
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.PASSWORD_RESET);
		
		reset(TOKEN, NEW_PASSWORD)
				.andExpect(status().isOk());
		
		login(NEW_PASSWORD)
				.andExpect(status().isOk());
	}
	
	@Test
	@DisplayName("재설정에 성공하면 그 계정의 모든 세션을 폐기")
	void passwordResetAPI_test3() throws Exception {
		Account account = savePasswordAccount();
		String web = tokenService.issueRefreshToken(account.getId()).refreshToken();
		String app = tokenService.issueRefreshToken(account.getId()).refreshToken();
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.PASSWORD_RESET);
		
		reset(TOKEN, NEW_PASSWORD)
				.andExpect(status().isOk());
		
		refreshWithBody(web)
				.andExpect(status().isUnauthorized());
		
		refreshWithBody(app)
				.andExpect(status().isUnauthorized());
	}
	
	@Test
	@DisplayName("비밀번호 규칙 위반은 INVALID_PASSWORD_PATTERN, 현재와 같은 비밀번호는 NEW_PASSWORD_SAME_AS_CURRENT")
	void passwordResetAPI_test4() throws Exception {
		Account account = savePasswordAccount();
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.PASSWORD_RESET);
		
		reset(TOKEN, "short1!")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PASSWORD_PATTERN"));
		
		reset(TOKEN, OLD_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("NEW_PASSWORD_SAME_AS_CURRENT"));
	}
	
	@Test
	@DisplayName("실패하면 토큰이 소모되지 않아 같은 토큰으로 다시 시도 가능")
	void passwordResetAPI_test5() throws Exception {
		Account account = savePasswordAccount();
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.PASSWORD_RESET);
		
		reset(TOKEN, OLD_PASSWORD)
				.andExpect(status().isBadRequest());
		
		reset(TOKEN, NEW_PASSWORD)
				.andExpect(status().isOk());
		
		// 성공한 토큰은 다시 쓸 수 없다
		reset(TOKEN, "another1!")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
	}
	
	@Test
	@DisplayName("잘못된 토큰과 다른 목적(SIGN_UP)의 토큰은 400 INVALID_VERIFICATION_TOKEN")
	void passwordResetAPI_test6() throws Exception {
		Account account = savePasswordAccount();
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.SIGN_UP);
		
		reset("unknown-token", NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
		
		reset(TOKEN, NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
	}
	
	@Test
	@DisplayName("인증 후 탈퇴한 계정은 403 ACCOUNT_WITHDRAWN")
	void passwordResetAPI_test7() throws Exception {
		Account account = savePasswordAccount();
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.PASSWORD_RESET);
		
		// 탈퇴 기능이 아직 없어 상태를 SQL로 바꾼다
		jdbcTemplate.update("UPDATE account.accounts SET status = 'WITHDRAWN' WHERE id = ?", account.getId());
		
		reset(TOKEN, NEW_PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_WITHDRAWN"));
	}
	
	@Test
	@DisplayName("인증 후 계정 이메일이 바뀌었으면 400 INVALID_VERIFICATION_TOKEN")
	void passwordResetAPI_test8() throws Exception {
		Account account = savePasswordAccount();
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.PASSWORD_RESET);
		
		// 대표 이메일 변경 API가 아직 없어 SQL로 바꾼다
		jdbcTemplate.update("UPDATE account.accounts SET email = ? WHERE id = ?", "changed@example.com", account.getId());
		
		reset(TOKEN, NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
	}
	
	@Test
	@DisplayName("로그인 5회 실패로 막혀도 재설정하면 같은 IP에서 새 비밀번호로 로그인 가능")
	void passwordResetAPI_test9() throws Exception {
		Account account = savePasswordAccount();
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.PASSWORD_RESET);
		
		for (int i = 0; i < 5; i++) {
			login("wrong123!");
		}
		
		login(OLD_PASSWORD)
				.andExpect(status().isTooManyRequests());
		
		reset(TOKEN, NEW_PASSWORD)
				.andExpect(status().isOk());
		
		login(NEW_PASSWORD)
				.andExpect(status().isOk());
	}
	
	private ResultActions reset(String verificationToken, String newPassword) throws Exception {
		return mockMvc.perform(post(RESET_URL)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"verificationToken": "%s", "newPassword": "%s"}
						""".formatted(verificationToken, newPassword)
				));
	}
	
	// 재설정 요청과 같은 IP(MockMvc 기본 127.0.0.1)로 로그인
	private ResultActions login(String password) throws Exception {
		return mockMvc.perform(post(LOGIN_URL)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"email": "%s", "password": "%s"}
						""".formatted(EMAIL, password)
				));
	}
	
	private ResultActions refreshWithBody(String token) throws Exception {
		return mockMvc.perform(post(REFRESH_URL)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"refreshToken": "%s"}
						""".formatted(token)
				));
	}
	
	private Account savePasswordAccount() {
		Account account = newAccount();
		account.registerPassword(passwordHasher.hash(OLD_PASSWORD), Instant.now());
		
		return accountRepository.save(account);
	}
	
	// 비밀번호가 없는 계정 (소셜 전용 계정과 같은 상태)
	private Account saveAccountWithoutPassword() {
		return accountRepository.save(newAccount());
	}
	
	private Account newAccount() {
		Instant now = Instant.now();
		
		return Account.register(
				PublicIdentifier.of("재설정회원", "RESET001"),
				Email.of(EMAIL),
				now,
				now
		);
	}
	
	// 코드 검증까지 끝난 인증 요청 저장 (SignupApiTest와 같은 방식, 대상 계정 포함)
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