package ticketing.account.account.presentation;

import static org.hamcrest.Matchers.containsString;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.application.port.PasswordHasher;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.domain.repository.AccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class EmailLoginApiTest {

	private static final String EMAIL = "user@example.com";
	
	private static final String PASSWORD = "abcd123!";
	
	private static final String IP = "10.0.0.1";
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private AccountRepository accountRepository;
	
	@Autowired
	private PasswordHasher passwordHasher;
	
	@Autowired
	private RedisConnectionFactory redisConnectionFactory;
	
	// 실패 카운터가 다음 테스트에 남지 않게 한다.
	@BeforeEach
	void clearRedis() {
		try (RedisConnection connection = redisConnectionFactory.getConnection()) {
			connection.serverCommands().flushDb();
		}
	}
	
	@Test
	@DisplayName("웹: 로그인 성공 시 200, 토큰과 계정 정보, Refresh Token은 쿠키로 전달")
	void emailLoginAPI_test1() throws Exception {
		saveAccount(EMAIL, "LOGIN001", PASSWORD);
		
		login(EMAIL, PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.expiresIn").value(900))
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andExpect(jsonPath("$.data.account.email").value(EMAIL))
				.andExpect(jsonPath("$.data.account.status").value("ACTIVE"))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")));
	}
	
	@Test
	@DisplayName("앱: 로그인 성공 시 Refresh Token을 바디로 전달, 쿠키 없음")
	void emailLoginAPI_test2() throws Exception {
		saveAccount(EMAIL, "LOGIN001", PASSWORD);
		
		mockMvc.perform(loginRequest(EMAIL, PASSWORD, IP).header("X-Client-Type", "APP"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}
	
	@Test
	@DisplayName("대소문자와 앞뒤 공백이 다른 이메일로도 로그인")
	void emailLoginAPI_test3() throws Exception {
		saveAccount(EMAIL, "LOGIN001", PASSWORD);
		
		login("  User@Example.COM  ", PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.account.email").value(EMAIL));
	}
	
	@Test
	@DisplayName("비밀번호 불일치, 없는 이메일, 소셜 전용 계정 모두 401 INVALID_CREDENTIALS")
	void emailLoginAPI_test4() throws Exception {
		saveAccount(EMAIL, "LOGIN001", PASSWORD);
		saveKakaoAccount("social@example.com", "SOCIAL01");
		
		login(EMAIL, "wrong123!")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		
		login("nobody@example.com", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		
		login("social@example.com", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}
	
	@Test
	@DisplayName("형식이 잘못된 이메일, 빈 이메일, 빈 비밀번호도 401 INVALID_CREDENTIALS")
	void emailLoginAPI_test5() throws Exception {
		login("not-an-email", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		
		login("", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		
		login(EMAIL, "")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}
	
	@Test
	@DisplayName("5번 실패하면 6번째는 429와 Retry-After, 이후 맞는 비밀번호도 거부")
	void emailLoginAPI_test6() throws Exception {
		saveAccount(EMAIL, "LOGIN001", PASSWORD);
		
		for (int i = 0; i < 5; i++) {
			login(EMAIL, "wrong123!")
					.andExpect(status().isUnauthorized());
		}
		
		login(EMAIL, "wrong123!")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
				.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
		
		login(EMAIL, PASSWORD)
				.andExpect(status().isTooManyRequests());
	}
	
	@Test
	@DisplayName("빈 비밀번호도 실패로 세서 5번 뒤에는 맞는 비밀번호도 429")
	void emailLoginAPI_test7() throws Exception {
		saveAccount(EMAIL, "LOGIN001", PASSWORD);
		
		for (int i = 0; i < 5; i++) {
			login(EMAIL, "")
					.andExpect(status().isUnauthorized());
		}
		
		login(EMAIL, PASSWORD)
				.andExpect(status().isTooManyRequests());
	}
	
	@Test
	@DisplayName("형식이 잘못된 이메일도 실패로 세서 5번 뒤에는 429")
	void emailLoginAPI_test8() throws Exception {
		for (int i = 0; i < 5; i++) {
			login("not-an-email", PASSWORD)
					.andExpect(status().isUnauthorized());
		}
		
		login("not-an-email", PASSWORD)
				.andExpect(status().isTooManyRequests());
	}
	
	@Test
	@DisplayName("없는 이메일도 같은 한도를 적용해 429 여부로 가입 여부를 알 수 없음")
	void emailLoginAPI_test9() throws Exception {
		for (int i = 0; i < 5; i++) {
			login("nobody@example.com", PASSWORD)
					.andExpect(status().isUnauthorized());
		}
		
		login("nobody@example.com", PASSWORD)
				.andExpect(status().isTooManyRequests());
	}
	
	@Test
	@DisplayName("로그인에 성공하면 이메일+IP 실패 횟수가 초기화")
	void emailLoginAPI_test10() throws Exception {
		saveAccount(EMAIL, "LOGIN001", PASSWORD);
		
		for (int i = 0; i < 4; i++) {
			login(EMAIL, "wrong123!");
		}
		
		login(EMAIL, PASSWORD)
				.andExpect(status().isOk());
		
		// 초기화되지 않았다면 5번째 실패 후 6번째에서 429가 난다.
		for (int i = 0; i < 4; i++) {
			login(EMAIL, "wrong123!")
					.andExpect(status().isUnauthorized());
		}
	}
	
	@Test
	@DisplayName("이메일+IP 한도는 IP마다 따로 적용")
	void emailLoginAPI_test11() throws Exception {
		saveAccount(EMAIL, "LOGIN001", PASSWORD);
		
		for (int i = 0; i < 5; i++) {
			login(EMAIL, "wrong123!");
		}
		
		mockMvc.perform(loginRequest(EMAIL, PASSWORD, "10.0.0.2"))
				.andExpect(status().isOk());
	}
	
	// X-Client-Type 없이 요청 (WEB)
	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(loginRequest(email, password, IP));
	}
	
	private MockHttpServletRequestBuilder loginRequest(String email, String password, String clientIp) {
		return post("/api/v1/auth/login")
				.with(servletRequest -> {
					servletRequest.setRemoteAddr(clientIp);
					return servletRequest;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"email": "%s", "password": "%s"}
						""".formatted(email, password)
				);
	}
	
	// 비밀번호가 있는 이메일 계정 저장
	private Account saveAccount(String email, String tag, String password) {
		Instant now = Instant.now();
		
		Account account = Account.register(
				PublicIdentifier.of("티켓러", tag),
				Email.of(email),
				now,
				now
		);
		
		account.registerPassword(passwordHasher.hash(password), now);
		
		return accountRepository.save(account);
	}
	
	// 비밀번호 없이 카카오만 연결된 계정 저장
	private Account saveKakaoAccount(String email, String tag) {
		Instant now = Instant.now();
		
		Account account = Account.register(
				PublicIdentifier.of("티켓러", tag),
				Email.of(email),
				now,
				now
		);
		
		account.connectSocialAccount(SocialProvider.KAKAO, "1234567890", now);
		
		return accountRepository.save(account);
	}
}