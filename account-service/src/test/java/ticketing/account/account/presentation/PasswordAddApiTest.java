// referenceFilePath: ../ticketing/account-service/src/test/java/ticketing/account/account/presentation/PasswordAddApiTest.java
package ticketing.account.account.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
public class PasswordAddApiTest {

	private static final String ADD_URL = "/api/v1/accounts/me/password";
	
	private static final String LOGIN_URL = "/api/v1/auth/login";
	
	private static final String REFRESH_URL = "/api/v1/auth/refresh";
	
	private static final String EMAIL = "social@example.com";
	
	private static final String PASSWORD = "newpass1!";
	
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
	
	@Test
	@DisplayName("소셜 전용 계정이 비밀번호를 추가하면 201 SUCCESS, 이메일 로그인 가능, 기존 세션 유지")
	void passwordAddAPI_test1() throws Exception {
		Account account = saveSocialAccount(EMAIL, "SOCIAL01");
		String refreshToken = tokenService.issueRefreshToken(account.getId()).refreshToken();
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.SENSITIVE_ACTION);
		
		add(account, PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data").doesNotExist());
		
		login(EMAIL, PASSWORD)
				.andExpect(status().isOk());
		
		// 비밀번호 추가는 세션을 폐기하지 않음 (DEC-04)
		refreshWithBody(refreshToken)
				.andExpect(status().isOk());
	}
	
	@Test
	@DisplayName("이미 비밀번호가 있으면 409 CREDENTIAL_ALREADY_REGISTERED, 토큰은 소모되지 않음")
	void passwordAddAPI_test2() throws Exception {
		Account account = savePasswordAccount(EMAIL, "PASS01");
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.SENSITIVE_ACTION);
		
		add(account, PASSWORD)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CREDENTIAL_ALREADY_REGISTERED"));
		
		assertTokenNotConsumed();
	}
	
	@Test
	@DisplayName("다른 계정에 귀속된 토큰은 400 INVALID_VERIFICATION_TOKEN, 토큰은 소모되지 않음")
	void passwordAddAPI_test3() throws Exception {
		Account account = saveSocialAccount(EMAIL, "SOCIAL01");
		Account other = saveSocialAccount("other@example.com", "OTHER01");
		saveVerified(other.getId(), "other@example.com", EmailVerificationPurpose.SENSITIVE_ACTION);
		
		add(account, PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
		
		assertTokenNotConsumed();
	}
	
	@Test
	@DisplayName("다른 목적(PASSWORD_RESET)의 토큰은 400 INVALID_VERIFICATION_TOKEN")
	void passwordAddAPI_test4() throws Exception {
		Account account = saveSocialAccount(EMAIL, "SOCIAL01");
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.PASSWORD_RESET);
		
		add(account, PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
	}
	
	@Test
	@DisplayName("비밀번호 규칙 위반은 400 INVALID_PASSWORD_PATTERN, 토큰은 소모되지 않음")
	void passwordAddAPI_test5() throws Exception {
		Account account = saveSocialAccount(EMAIL, "SOCIAL01");
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.SENSITIVE_ACTION);
		
		add(account, "short1!")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PASSWORD_PATTERN"));
		
		assertTokenNotConsumed();
	}
	
	@Test
	@DisplayName("인증 후 계정 이메일이 바뀌었으면 400 INVALID_VERIFICATION_TOKEN")
	void passwordAddAPI_test6() throws Exception {
		Account account = saveSocialAccount(EMAIL, "SOCIAL01");
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.SENSITIVE_ACTION);
		
		// 대표 이메일 변경 API가 아직 없어 SQL로 바꾼다
		jdbcTemplate.update(
				"UPDATE account.accounts SET email = ? WHERE id = ?",
				"changed@example.com",
				account.getId()
		);
		
		add(account, PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
	}
	
	@Test
	@DisplayName("정지(SUSPENDED) 계정도 비밀번호를 추가할 수 있음")
	void passwordAddAPI_test7() throws Exception {
		Account account = saveSocialAccount(EMAIL, "SOCIAL01");
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.SENSITIVE_ACTION);
		
		jdbcTemplate.update(
				"UPDATE account.accounts SET status = 'SUSPENDED' WHERE id = ?",
				account.getId()
		);
		
		add(account, PASSWORD)
				.andExpect(status().isCreated());
		
		login(EMAIL, PASSWORD)
				.andExpect(status().isOk());
	}
	
	@Test
	@DisplayName("탈퇴한 계정의 Access Token이면 409 ACCOUNT_NOT_ACTIVE")
	void passwordAddAPI_test8() throws Exception {
		Account account = saveSocialAccount(EMAIL, "SOCIAL01");
		saveVerified(account.getId(), EMAIL, EmailVerificationPurpose.SENSITIVE_ACTION);
		
		jdbcTemplate.update(
				"UPDATE account.accounts SET status = 'WITHDRAWN' WHERE id = ?",
				account.getId()
		);
		
		add(account, PASSWORD)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
	}
	
	@Test
	@DisplayName("Access Token이 없으면 401 UNAUTHORIZED")
	void passwordAddAPI_test9() throws Exception {
		mockMvc.perform(post(ADD_URL)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
	
	private ResultActions add(Account account, String password) throws Exception {
		return mockMvc.perform(post(ADD_URL)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(password)));
	}
	
	private String body(String password) {
		return """
				{"password": "%s", "verificationToken": "%s"}
				""".formatted(password, TOKEN);
	}
	
	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post(LOGIN_URL)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"email": "%s", "password": "%s"}
						""".formatted(email, password)
				));
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
	
	// 실패 후에도 인증 완료 토큰이 사용 처리되지 않았는지 확인
	private void assertTokenNotConsumed() {
		assertThat(jpaEmailVerificationRepository.findAll())
				.singleElement()
				.satisfies(verification -> assertThat(verification.getConsumedAt()).isNull());
	}
	
	// 비밀번호 없이 카카오만 연결된 계정
	private Account saveSocialAccount(String email, String tag) {
		Account account = newAccount(email, tag);
		
		account.connectSocialAccount(SocialProvider.KAKAO, "kakao-" + tag, Instant.now());
		
		return accountRepository.save(account);
	}
	
	private Account savePasswordAccount(String email, String tag) {
		Account account = newAccount(email, tag);
		
		account.registerPassword(passwordHasher.hash("oldpass1!"), Instant.now());
		
		return accountRepository.save(account);
	}
	
	private Account newAccount(String email, String tag) {
		Instant now = Instant.now();
		
		return Account.register(
				PublicIdentifier.of("추가회원", tag),
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