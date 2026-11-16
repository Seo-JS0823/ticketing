// referenceFilePath: ../ticketing/account-service/src/test/java/ticketing/account/account/presentation/MyAccountApiTest.java
package ticketing.account.account.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountRole;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.auth.application.port.AccessTokenIssuer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class MyAccountApiTest {

	private static final String ME_URL = "/api/v1/accounts/me";
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private AccountRepository accountRepository;
	
	@Autowired
	private AccessTokenIssuer accessTokenIssuer;
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	@Test
	@DisplayName("비밀번호 계정은 계정 정보, hasPassword true, 빈 소셜 연결 목록을 반환")
	void myAccountAPI_test1() throws Exception {
		Account account = savePasswordAccount("me@example.com", "ME01");
		
		getMe(accessToken(account.getId(), AccountStatus.ACTIVE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.accountId").value(account.getId().toString()))
				.andExpect(jsonPath("$.data.email").value("me@example.com"))
				.andExpect(jsonPath("$.data.emailVerifiedAt").exists())
				.andExpect(jsonPath("$.data.nickname").value("내정보회원"))
				.andExpect(jsonPath("$.data.tag").value("ME01"))
				.andExpect(jsonPath("$.data.role").value("USER"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.hasPassword").value(true))
				.andExpect(jsonPath("$.data.socialAccounts").isEmpty())
				.andExpect(jsonPath("$.data.createdAt").exists())
				.andExpect(jsonPath("$.data.suspension").doesNotExist());
	}
	
	@Test
	@DisplayName("소셜 전용 계정은 hasPassword false, 연결된 KAKAO를 반환하고 providerUserId는 노출하지 않음")
	void myAccountAPI_test2() throws Exception {
		Account account = saveKakaoAccount("kakao@example.com", "KAKAO01");
		
		getMe(accessToken(account.getId(), AccountStatus.ACTIVE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasPassword").value(false))
				.andExpect(jsonPath("$.data.socialAccounts.length()").value(1))
				.andExpect(jsonPath("$.data.socialAccounts[0].provider").value("KAKAO"))
				.andExpect(jsonPath("$.data.socialAccounts[0].linkedAt").exists())
				.andExpect(jsonPath("$.data.socialAccounts[0].providerUserId").doesNotExist());
	}
	
	@Test
	@DisplayName("해제된(deleted_at이 있는) 소셜 연결은 목록에서 제외")
	void myAccountAPI_test3() throws Exception {
		Account account = saveKakaoAccount("unlinked@example.com", "UNLINK01");
		
		jdbcTemplate.update(
				"UPDATE account.social_accounts SET deleted_at = now() WHERE account_id = ?",
				account.getId()
		);
		
		getMe(accessToken(account.getId(), AccountStatus.ACTIVE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.socialAccounts").isEmpty());
	}
	
	@Test
	@DisplayName("정지(SUSPENDED) 계정도 조회할 수 있고 suspension은 반환하지 않음")
	void myAccountAPI_test4() throws Exception {
		Account account = savePasswordAccount("suspended@example.com", "SUSP01");
		
		jdbcTemplate.update(
				"UPDATE account.accounts SET status = 'SUSPENDED' WHERE id = ?",
				account.getId()
		);
		
		getMe(accessToken(account.getId(), AccountStatus.SUSPENDED))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SUSPENDED"))
				.andExpect(jsonPath("$.data.suspension").doesNotExist());
	}
	
	@Test
	@DisplayName("탈퇴한 계정의 Access Token이면 409 ACCOUNT_NOT_ACTIVE")
	void myAccountAPI_test5() throws Exception {
		Account account = savePasswordAccount("withdrawn@example.com", "WITH01");
		
		jdbcTemplate.update(
				"UPDATE account.accounts SET status = 'WITHDRAWN' WHERE id = ?",
				account.getId()
		);
		
		getMe(accessToken(account.getId(), AccountStatus.ACTIVE))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
	}
	
	@Test
	@DisplayName("토큰의 계정이 존재하지 않으면 401 UNAUTHORIZED")
	void myAccountAPI_test6() throws Exception {
		getMe(accessToken(UUID.randomUUID(), AccountStatus.ACTIVE))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
	
	@Test
	@DisplayName("Access Token이 없으면 401 UNAUTHORIZED")
	void myAccountAPI_test7() throws Exception {
		mockMvc.perform(get(ME_URL))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
	
	private ResultActions getMe(String accessToken) throws Exception {
		return mockMvc.perform(get(ME_URL)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}
	
	// 실제 발급기로 만든 Access Token
	private String accessToken(UUID accountId, AccountStatus status) {
		return accessTokenIssuer.issue(accountId, AccountRole.USER, status).accessToken();
	}
	
	private Account savePasswordAccount(String email, String tag) {
		Account account = newAccount(email, tag);
		
		account.registerPassword("password-hash", Instant.now());
		
		return accountRepository.save(account);
	}
	
	private Account saveKakaoAccount(String email, String tag) {
		Account account = newAccount(email, tag);
		
		account.connectSocialAccount(SocialProvider.KAKAO, "kakao-" + tag, Instant.now());
		
		return accountRepository.save(account);
	}
	
	private Account newAccount(String email, String tag) {
		Instant now = Instant.now();
		
		return Account.register(
				PublicIdentifier.of("내정보회원", tag),
				Email.of(email),
				now,
				now
		);
	}
}