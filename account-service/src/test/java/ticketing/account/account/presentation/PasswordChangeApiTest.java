// referenceFilePath: ../ticketing/account-service/src/test/java/ticketing/account/account/presentation/PasswordChangeApiTest.java
package ticketing.account.account.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.auth.application.port.AccessTokenIssuer;
import ticketing.account.auth.application.service.TokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class PasswordChangeApiTest {

	private static final String CHANGE_URL = "/api/v1/accounts/me/password";
	
	private static final String LOGIN_URL = "/api/v1/auth/login";
	
	private static final String REFRESH_URL = "/api/v1/auth/refresh";
	
	private static final String EMAIL = "change@example.com";
	
	private static final String OLD_PASSWORD = "oldpass1!";
	
	private static final String NEW_PASSWORD = "newpass1!";
	
	private static final String WRONG_PASSWORD = "wrong123!";
	
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
	@DisplayName("변경 성공 시 200 SUCCESS, 새 비밀번호로 로그인되고 이전 비밀번호는 실패")
	void passwordChangeAPI_test1() throws Exception {
		Account account = savePasswordAccount();
		
		change(account, OLD_PASSWORD, NEW_PASSWORD)
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
	@DisplayName("변경에 성공하면 그 계정의 모든 세션을 폐기")
	void passwordChangeAPI_test2() throws Exception {
		Account account = savePasswordAccount();
		String web = tokenService.issueRefreshToken(account.getId()).refreshToken();
		String app = tokenService.issueRefreshToken(account.getId()).refreshToken();
		
		change(account, OLD_PASSWORD, NEW_PASSWORD)
				.andExpect(status().isOk());
		
		refreshWithBody(web)
				.andExpect(status().isUnauthorized());
		
		refreshWithBody(app)
				.andExpect(status().isUnauthorized());
	}
	
	@Test
	@DisplayName("현재 비밀번호가 틀리면 401 INVALID_CREDENTIALS, 비밀번호와 세션은 유지")
	void passwordChangeAPI_test3() throws Exception {
		Account account = savePasswordAccount();
		String refreshToken = tokenService.issueRefreshToken(account.getId()).refreshToken();
		
		change(account, WRONG_PASSWORD, NEW_PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		
		login(OLD_PASSWORD)
				.andExpect(status().isOk());
		
		refreshWithBody(refreshToken)
				.andExpect(status().isOk());
	}
	
	@Test
	@DisplayName("현재 비밀번호가 틀리면 새 비밀번호가 규칙 위반이어도 401 (DEC-07)")
	void passwordChangeAPI_test4() throws Exception {
		Account account = savePasswordAccount();
		
		change(account, WRONG_PASSWORD, "short1!")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}
	
	@Test
	@DisplayName("새 비밀번호 규칙 위반은 400 INVALID_PASSWORD_PATTERN")
	void passwordChangeAPI_test5() throws Exception {
		Account account = savePasswordAccount();
		
		change(account, OLD_PASSWORD, "short1!")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PASSWORD_PATTERN"));
		
		login(OLD_PASSWORD)
				.andExpect(status().isOk());
	}
	
	@Test
	@DisplayName("새 비밀번호가 현재와 같으면 400 NEW_PASSWORD_SAME_AS_CURRENT")
	void passwordChangeAPI_test6() throws Exception {
		Account account = savePasswordAccount();
		
		change(account, OLD_PASSWORD, OLD_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("NEW_PASSWORD_SAME_AS_CURRENT"));
	}
	
	@Test
	@DisplayName("비밀번호가 없는 소셜 전용 계정은 409 CREDENTIAL_NOT_REGISTERED")
	void passwordChangeAPI_test7() throws Exception {
		Account account = saveSocialAccount();
		
		change(account, OLD_PASSWORD, NEW_PASSWORD)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CREDENTIAL_NOT_REGISTERED"));
	}
	
	@Test
	@DisplayName("정지(SUSPENDED) 계정도 비밀번호를 변경할 수 있음")
	void passwordChangeAPI_test8() throws Exception {
		Account account = savePasswordAccount();
		
		jdbcTemplate.update(
				"UPDATE account.accounts SET status = 'SUSPENDED' WHERE id = ?",
				account.getId()
		);
		
		change(account, OLD_PASSWORD, NEW_PASSWORD)
				.andExpect(status().isOk());
		
		login(NEW_PASSWORD)
				.andExpect(status().isOk());
	}
	
	@Test
	@DisplayName("탈퇴한 계정의 Access Token이면 409 ACCOUNT_NOT_ACTIVE")
	void passwordChangeAPI_test9() throws Exception {
		Account account = savePasswordAccount();
		
		jdbcTemplate.update(
				"UPDATE account.accounts SET status = 'WITHDRAWN' WHERE id = ?",
				account.getId()
		);
		
		change(account, OLD_PASSWORD, NEW_PASSWORD)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
	}
	
	@Test
	@DisplayName("현재 비밀번호를 5번 틀리면 맞는 비밀번호도 429, 로그인도 같은 한도로 거부")
	void passwordChangeAPI_test10() throws Exception {
		Account account = savePasswordAccount();
		
		for (int i = 0; i < 5; i++) {
			change(account, WRONG_PASSWORD, NEW_PASSWORD)
					.andExpect(status().isUnauthorized());
		}
		
		change(account, OLD_PASSWORD, NEW_PASSWORD)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
				.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
		
		// 로그인과 카운터를 공유 (DEC-05)
		login(OLD_PASSWORD)
				.andExpect(status().isTooManyRequests());
	}
	
	@Test
	@DisplayName("현재 비밀번호가 없으면 401 INVALID_CREDENTIALS")
	void passwordChangeAPI_test11() throws Exception {
		Account account = savePasswordAccount();
		
		mockMvc.perform(patch(CHANGE_URL)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account))
						.contentType(MediaType.APPLICATION_JSON)
						.content(
								"""
								{"newPassword": "%s"}
								""".formatted(NEW_PASSWORD)
						))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}
	
	@Test
	@DisplayName("변경에 성공하면 이 IP의 실패 횟수를 초기화")
	void passwordChangeAPI_test12() throws Exception {
		Account account = savePasswordAccount();
		
		for (int i = 0; i < 4; i++) {
			change(account, WRONG_PASSWORD, NEW_PASSWORD)
					.andExpect(status().isUnauthorized());
		}
		
		change(account, OLD_PASSWORD, NEW_PASSWORD)
				.andExpect(status().isOk());
		
		// 초기화되지 않았다면 4 + 1번째에서 429
		for (int i = 0; i < 5; i++) {
			login(WRONG_PASSWORD)
					.andExpect(status().isUnauthorized());
		}
	}
	
	@Test
	@DisplayName("Access Token이 없으면 401 UNAUTHORIZED")
	void passwordChangeAPI_test13() throws Exception {
		mockMvc.perform(patch(CHANGE_URL)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(OLD_PASSWORD, NEW_PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
	
	private ResultActions change(Account account, String currentPassword, String newPassword) throws Exception {
		return mockMvc.perform(patch(CHANGE_URL)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(currentPassword, newPassword)));
	}
	
	private String body(String currentPassword, String newPassword) {
		return """
				{"currentPassword": "%s", "newPassword": "%s"}
				""".formatted(currentPassword, newPassword);
	}
	
	// 변경 요청과 같은 IP(MockMvc 기본 127.0.0.1)로 로그인
	private ResultActions login(String password) throws Exception {
		return mockMvc.perform(post(LOGIN_URL)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"email": "%s", "password": "%s"}
						""".formatted(EMAIL, password)
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
	
	private Account savePasswordAccount() {
		Account account = newAccount();
		
		account.registerPassword(passwordHasher.hash(OLD_PASSWORD), Instant.now());
		
		return accountRepository.save(account);
	}
	
	// 비밀번호 없이 카카오만 연결된 계정
	private Account saveSocialAccount() {
		Account account = newAccount();
		
		account.connectSocialAccount(SocialProvider.KAKAO, "kakao-change", Instant.now());
		
		return accountRepository.save(account);
	}
	
	private Account newAccount() {
		Instant now = Instant.now();
		
		return Account.register(
				PublicIdentifier.of("변경회원", "CHANGE01"),
				Email.of(EMAIL),
				now,
				now
		);
	}
}