package ticketing.account.auth.presentation;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

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

import jakarta.servlet.http.Cookie;
import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
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
public class LogoutApiTest {

	private static final String LOGOUT_URL = "/api/v1/auth/logout";
	
	private static final String REFRESH_URL = "/api/v1/auth/refresh";
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private AccountRepository accountRepository;
	
	@Autowired
	private TokenService tokenService;
	
	@Autowired
	private AccessTokenIssuer accessTokenIssuer;
	
	@Test
	@DisplayName("웹: 쿠키 토큰으로 로그아웃하면 204, 쿠키 삭제, 그 토큰으로는 재발급 불가")
	void logoutAPI_test1() throws Exception {
		Account account = saveAccount("web@example.com", "LOGOUT01");
		String token = issueToken(account);
		
		mockMvc.perform(logout(account).cookie(new Cookie("refresh_token", token)))
				.andExpect(status().isNoContent())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
						containsString("refresh_token="),
						containsString("Max-Age=0"),
						containsString("Path=/api/v1/auth")
				)));
		
		refreshWithBody(token)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}
	
	@Test
	@DisplayName("앱: 바디 토큰으로 로그아웃하면 204, 그 토큰으로는 재발급 불가")
	void logoutAPI_test2() throws Exception {
		Account account = saveAccount("app@example.com", "LOGOUT02");
		String token = issueToken(account);
		
		mockMvc.perform(logout(account)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(token)))
				.andExpect(status().isNoContent());
		
		refreshWithBody(token)
				.andExpect(status().isUnauthorized());
	}
	
	@Test
	@DisplayName("로그아웃한 세션만 폐기하고 다른 기기의 세션은 유지")
	void logoutAPI_test3() throws Exception {
		Account account = saveAccount("multi@example.com", "LOGOUT03");
		String web = issueToken(account);
		String app = issueToken(account);
		
		mockMvc.perform(logout(account).cookie(new Cookie("refresh_token", web)))
				.andExpect(status().isNoContent());
		
		refreshWithBody(web)
				.andExpect(status().isUnauthorized());
		
		refreshWithBody(app)
				.andExpect(status().isOk());
	}
	
	@Test
	@DisplayName("다른 사용자의 Refresh Token으로 로그아웃하면 204지만 그 세션은 폐기하지 않음")
	void logoutAPI_test4() throws Exception {
		Account me = saveAccount("me@example.com", "LOGOUT04");
		Account other = saveAccount("other@example.com", "LOGOUT05");
		String othersToken = issueToken(other);
		
		mockMvc.perform(logout(me).cookie(new Cookie("refresh_token", othersToken)))
				.andExpect(status().isNoContent());
		
		refreshWithBody(othersToken)
				.andExpect(status().isOk());
	}
	
	@Test
	@DisplayName("Refresh Token이 없거나 모르는 토큰이어도 204, 쿠키 삭제는 항상 보냄")
	void logoutAPI_test5() throws Exception {
		Account account = saveAccount("none@example.com", "LOGOUT06");
		
		mockMvc.perform(logout(account))
				.andExpect(status().isNoContent())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
		
		mockMvc.perform(logout(account).cookie(new Cookie("refresh_token", "unknown-token")))
				.andExpect(status().isNoContent());
	}
	
	@Test
	@DisplayName("쿠키와 바디에 토큰이 모두 있으면 둘 다 폐기")
	void logoutAPI_test6() throws Exception {
		Account account = saveAccount("both@example.com", "LOGOUT07");
		String cookieToken = issueToken(account);
		String bodyToken = issueToken(account);
		
		mockMvc.perform(logout(account)
						.cookie(new Cookie("refresh_token", cookieToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(bodyToken)))
				.andExpect(status().isNoContent());
		
		refreshWithBody(cookieToken)
				.andExpect(status().isUnauthorized());
		
		refreshWithBody(bodyToken)
				.andExpect(status().isUnauthorized());
	}
	
	@Test
	@DisplayName("Access Token이 없거나 서명이 맞지 않으면 401 UNAUTHORIZED")
	void logoutAPI_test7() throws Exception {
		Account account = saveAccount("auth@example.com", "LOGOUT08");
		
		mockMvc.perform(post(LOGOUT_URL))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		
		mockMvc.perform(post(LOGOUT_URL)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered(accessToken(account))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
	
	private MockHttpServletRequestBuilder logout(Account account) {
		return post(LOGOUT_URL)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(account));
	}
	
	private ResultActions refreshWithBody(String token) throws Exception {
		return mockMvc.perform(post(REFRESH_URL)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(token)));
	}
	
	private String body(String token) {
		return """
				{"refreshToken": "%s"}
				""".formatted(token);
	}
	
	// 실제 발급기로 만든 Access Token (JWT 검증 설정까지 함께 확인)
	private String accessToken(Account account) {
		return accessTokenIssuer.issue(
				account.getId(),
				account.getRole(),
				account.getStatus()
		).accessToken();
	}
	
	// 내용(payload)만 바꿔 서명이 맞지 않는 토큰
	private String tampered(String jwt) {
		String[] parts = jwt.split("\\.");
		
		String payload = Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString("{\"sub\":\"tampered\"}".getBytes(StandardCharsets.UTF_8));
		
		return parts[0] + "." + payload + "." + parts[2];
	}
	
	private Account saveAccount(String email, String tag) {
		Instant now = Instant.now();
		
		return accountRepository.save(
				Account.register(
						PublicIdentifier.of("로그아웃회원", tag),
						Email.of(email),
						now,
						now
				)
		);
	}
	
	private String issueToken(Account account) {
		return tokenService.issueRefreshToken(account.getId()).refreshToken();
	}
}