package ticketing.account.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;
import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.auth.application.service.TokenService;
import ticketing.account.global.security.AccessTokenClaims;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class TokenRefreshApiTest {

	private static final String REFRESH_URL = "/api/v1/auth/refresh";
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private AccountRepository accountRepository;
	
	@Autowired
	private TokenService tokenService;
	
	@Autowired
	private JwtDecoder jwtDecoder;
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	@Test
	@DisplayName("웹: 쿠키로 요청하면 200, 새 Refresh Token은 쿠키로만 전달")
	void tokenRefreshAPI_test1() throws Exception {
		String token = issueToken(saveAccount());
		
		refreshWithCookie(token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")));
	}
	
	@Test
	@DisplayName("쿠키로 요청하면 X-Client-Type이 APP이어도 바디에 Refresh Token을 넣지 않음")
	void tokenRefreshAPI_test2() throws Exception {
		String token = issueToken(saveAccount());
		
		mockMvc.perform(post(REFRESH_URL)
						.cookie(new Cookie("refresh_token", token))
						.header("X-Client-Type", "APP"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")));
	}
	
	@Test
	@DisplayName("앱: 바디로 요청하면 새 Refresh Token을 바디로만 전달, 쿠키 없음")
	void tokenRefreshAPI_test3() throws Exception {
		String token = issueToken(saveAccount());
		
		refreshWithBody(token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}
	
	@Test
	@DisplayName("쿠키와 바디에 모두 있거나, 둘 다 없거나, 빈 값이면 401 INVALID_REFRESH_TOKEN")
	void tokenRefreshAPI_test4() throws Exception {
		String token = issueToken(saveAccount());
		
		mockMvc.perform(post(REFRESH_URL)
						.cookie(new Cookie("refresh_token", token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(token)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
		
		mockMvc.perform(post(REFRESH_URL))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
		
		refreshWithBody("")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}
	
	@Test
	@DisplayName("재발급 후 이전 토큰으로 다시 요청하면 401이고, 그 세션의 새 토큰도 폐기")
	void tokenRefreshAPI_test5() throws Exception {
		String first = issueToken(saveAccount());
		
		String second = JsonPath.read(
				refreshWithBody(first).andReturn().getResponse().getContentAsString(),
				"$.data.refreshToken"
		);
		
		refreshWithBody(first)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
		
		refreshWithBody(second)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}
	
	@Test
	@DisplayName("정지 계정은 재발급하고, 응답과 Access Token의 status에 SUSPENDED 반영")
	void tokenRefreshAPI_test6() throws Exception {
		Account account = saveAccount();
		String token = issueToken(account);
		
		changeStatus(account.getId(), "SUSPENDED");
		
		String accessToken = JsonPath.read(
				refreshWithBody(token)
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.account.status").value("SUSPENDED"))
						.andReturn().getResponse().getContentAsString(),
				"$.data.accessToken"
		);
		
		assertThat(jwtDecoder.decode(accessToken).getClaimAsString(AccessTokenClaims.STATUS))
				.isEqualTo("SUSPENDED");
	}
	
	@Test
	@DisplayName("탈퇴 계정은 403 ACCOUNT_WITHDRAWN이고 그 세션의 토큰을 모두 폐기")
	void tokenRefreshAPI_test7() throws Exception {
		Account account = saveAccount();
		String token = issueToken(account);
		
		changeStatus(account.getId(), "WITHDRAWN");
		
		refreshWithBody(token)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_WITHDRAWN"));
		
		assertThat(activeTokenCount(account.getId())).isZero();
	}
	
	private ResultActions refreshWithCookie(String token) throws Exception {
		return mockMvc.perform(post(REFRESH_URL).cookie(new Cookie("refresh_token", token)));
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
	
	private Account saveAccount() {
		Instant now = Instant.now();
		
		return accountRepository.save(
				Account.register(
						PublicIdentifier.of("재발급회원", "REFRESH1"),
						Email.of("refresh@example.com"),
						now,
						now
				)
		);
	}
	
	private String issueToken(Account account) {
		return tokenService.issueRefreshToken(account.getId()).refreshToken();
	}
	
	// 정지·탈퇴 기능이 아직 없어 상태를 SQL로 바꾼다 (테스트 계획 REQ-03)
	private void changeStatus(UUID accountId, String status) {
		jdbcTemplate.update("UPDATE account.accounts SET status = ? WHERE id = ?", status, accountId);
	}
	
	// 폐기되지 않은 토큰 수
	private int activeTokenCount(UUID accountId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM account.refresh_tokens WHERE account_id = ? AND revoked_at IS NULL",
				Integer.class,
				accountId
		);
	}
}