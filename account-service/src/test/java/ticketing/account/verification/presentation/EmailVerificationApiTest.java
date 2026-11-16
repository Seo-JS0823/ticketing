package ticketing.account.verification.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountRole;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.auth.application.port.AccessTokenIssuer;
import ticketing.account.global.crypto.TokenCrypto;
import ticketing.account.verification.application.dto.VerificationMailType;
import ticketing.account.verification.application.event.VerificationMailRequested;
import ticketing.account.verification.application.port.VerificationMailSender;
import ticketing.account.verification.domain.model.EmailVerification;
import ticketing.account.verification.domain.model.EmailVerificationPurpose;
import ticketing.account.verification.domain.model.EmailVerificationStatus;
import ticketing.account.verification.infra.persistence.JpaEmailVerificationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class EmailVerificationApiTest {

	private static final long MAIL_WAIT_MILLIS = 5000;
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private JpaEmailVerificationRepository jpaEmailVerificationRepository;
	
	@Autowired
	private AccountRepository accountRepository;
	
	@Autowired
	private TokenCrypto tokenCrypto;
	
	@MockitoBean
	private VerificationMailSender verificationMailSender;
	
	@Autowired
	private RedisConnectionFactory redisConnectionFactory;
	
	@Autowired
	private AccessTokenIssuer accessTokenIssuer;
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	@BeforeEach
	void clearRedis() {
		try (RedisConnection connection = redisConnectionFactory.getConnection()) {
			connection.serverCommands().flushDb();
		}
	}
	
	@Test
	@DisplayName("미가입 이메일로 가입 인증을 요청하면 코드를 해시로 저장하고 메일을 보낸다.")
	void emailVerificationAPI_test1() throws Exception {
		EmailVerification saved = findVerification(request("new-user@example.com", "SIGN_UP"));
		
		VerificationMailRequested mail = captureMail();
		
		assertThat(mail.type()).isEqualTo(VerificationMailType.CODE);
		assertThat(mail.to()).isEqualTo("new-user@example.com");
		assertThat(mail.code()).matches("^[0-9]{6}$");
		assertThat(saved.getStatus()).isEqualTo(EmailVerificationStatus.PENDING);
		assertThat(saved.getCodeHash()).isEqualTo(tokenCrypto.hash(mail.code()));
		assertThat(saved.getCodeHash()).hasSize(64);
	}
	
	@Test
	@DisplayName("가입된 이메일로 가입 인증을 요청해도 같은 응답을 주고 안내 메일만 보낸다")
	void emailVerificationAPI_test2() throws Exception {
		saveAccount("registered@example.com", "REG1");
		
		findVerification(request("registered@example.com", "SIGN_UP"));
		
		assertThat(captureMail().type()).isEqualTo(VerificationMailType.ALREADY_REGISTERED);
	}
	
	@Test
	@DisplayName("없는 계정의 비밀번호 재설정은 요청 행만 만들고 메일을 보내지 않는다.")
	void emailVerificationAPI_test3() throws Exception {
		EmailVerification saved = findVerification(request("nobody@example.com", "PASSWORD_RESET"));
		
		verify(verificationMailSender, after(500).never()).send(any());
		
		assertThat(saved.getAccountId()).isNull();
	}
	
	@Test
	@DisplayName("가입된 계정의 비밀번호 재설정은 계정에 귀속되고 인증 코드를 보낸다")
	void emailVerificationAPI_test4() throws Exception {
		Account account = saveAccount("reset@example.com", "RES1");
		
		EmailVerification saved = findVerification(request("RESET@example.com", "PASSWORD_RESET"));
		
		assertThat(captureMail().type()).isEqualTo(VerificationMailType.CODE);
		assertThat(saved.getAccountId()).isEqualTo(account.getId());
	}
	
	@Test
	@DisplayName("인증 목적이 없으면 INVALID_REQUEST")
	void emailVerificationAPI_test5() throws Exception {
		request("user@example.com", "UNKNOWN")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
	
	@Test
	@DisplayName("이메일이 없으면 EMAIL_REQUIRED")
	void emailVerificationAPI_test6() throws Exception {
		mockMvc.perform(post("/api/v1/email-verifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"purpose":"SIGN_UP"}
						"""
				))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMAIL_REQUIRED"));
		
	}
	
	@Test
	@DisplayName("SENSITIVE_ACTION은 email 없이 요청하면 로그인 계정의 이메일로 코드를 보내고 그 계정에 귀속")
	void emailVerificationAPI_test7() throws Exception {
		Account account = saveAccount("owner@example.com", "OWN1");
		
		EmailVerification saved = findVerification(requestSensitiveAction(
				"""
				{"purpose": "SENSITIVE_ACTION"}
				""",
				accessToken(account.getId())
		));
		
		VerificationMailRequested mail = captureMail();
		
		assertThat(mail.type()).isEqualTo(VerificationMailType.CODE);
		assertThat(mail.to()).isEqualTo("owner@example.com");
		assertThat(saved.getAccountId()).isEqualTo(account.getId());
		assertThat(saved.getEmail()).isEqualTo(Email.of("owner@example.com"));
		assertThat(saved.getPurpose()).isEqualTo(EmailVerificationPurpose.SENSITIVE_ACTION);
	}
	
	@Test
	@DisplayName("SENSITIVE_ACTION은 바디의 email을 무시하고, 정지(SUSPENDED) 계정도 요청할 수 있음")
	void emailVerificationAPI_test8() throws Exception {
		Account account = saveAccount("suspended@example.com", "SUS1");
		
		jdbcTemplate.update(
				"UPDATE account.accounts SET status = 'SUSPENDED' WHERE id = ?",
				account.getId()
		);
		
		EmailVerification saved = findVerification(requestSensitiveAction(
				"""
				{"email": "%s", "purpose": "SENSITIVE_ACTION"}
				""".formatted("attacker@example.com"),
				accessToken(account.getId())
		));
		
		assertThat(captureMail().to()).isEqualTo("suspended@example.com");
		assertThat(saved.getEmail()).isEqualTo(Email.of("suspended@example.com"));
		assertThat(saved.getAccountId()).isEqualTo(account.getId());
	}
	
	@Test
	@DisplayName("Access Token 없이 SENSITIVE_ACTION을 요청하면 401 UNAUTHORIZED, 요청 행을 만들지 않음")
	void emailVerificationAPI_test9() throws Exception {
		requestSensitiveAction(
				"""
				{"email": "%s", "purpose": "SENSITIVE_ACTION"}
				""".formatted("nobody@example.com"),
				null
		)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		
		assertThat(jpaEmailVerificationRepository.count()).isZero();
	}
	
	@Test
	@DisplayName("탈퇴한 계정의 Access Token으로 SENSITIVE_ACTION을 요청하면 409 ACCOUNT_NOT_ACTIVE, 행과 메일 없음")
	void emailVerificationAPI_test10() throws Exception {
		Account account = saveAccount("withdrawn@example.com", "WDR1");
		
		jdbcTemplate.update(
				"UPDATE account.accounts SET status = 'WITHDRAWN' WHERE id = ?",
				account.getId()
		);
		
		requestSensitiveAction(
				"""
				{"purpose": "SENSITIVE_ACTION"}
				""",
				accessToken(account.getId())
		)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
		
		assertThat(jpaEmailVerificationRepository.count()).isZero();
		verify(verificationMailSender, after(500).never()).send(any());
	}
	
	@Test
	@DisplayName("토큰의 계정이 존재하지 않으면 401 UNAUTHORIZED")
	void emailVerificationAPI_test11() throws Exception {
		requestSensitiveAction(
				"""
				{"purpose": "SENSITIVE_ACTION"}
				""",
				accessToken(UUID.randomUUID())
		)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
	
	@Test
	@DisplayName("SENSITIVE_ACTION 재발송은 같은 계정에 귀속되고 계정의 현재 이메일로 보냄")
	void emailVerificationAPI_test12() throws Exception {
		Account account = saveAccount("before@example.com", "RSD1");
		
		// 60초 재발송 간격을 지난 기존 요청
		EmailVerification previous = savePendingSensitiveAction(account.getId(), "before@example.com");
		
		// 요청 후 대표 이메일이 바뀐 상황 (대표 이메일 변경 기능이 없어 SQL로 만듦)
		jdbcTemplate.update(
				"UPDATE account.accounts SET email = ? WHERE id = ?",
				"after@example.com",
				account.getId()
		);
		
		EmailVerification resend = findVerification(
				resend(previous.getId(), accessToken(account.getId()))
		);
		
		assertThat(captureMail().to()).isEqualTo("after@example.com");
		assertThat(resend.getAccountId()).isEqualTo(account.getId());
		assertThat(resend.getEmail()).isEqualTo(Email.of("after@example.com"));
	}
	
	@Test
	@DisplayName("Access Token 없이 SENSITIVE_ACTION을 재발송하면 401 UNAUTHORIZED, 기존 요청 유지")
	void emailVerificationAPI_test13() throws Exception {
		Account account = saveAccount("owner@example.com", "RSD2");
		EmailVerification previous = savePendingSensitiveAction(account.getId(), "owner@example.com");
		
		resend(previous.getId(), null)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		
		assertPendingOnly(previous);
	}
	
	@Test
	@DisplayName("다른 계정의 SENSITIVE_ACTION 요청을 재발송하면 404 VERIFICATION_NOT_FOUND, 기존 요청 유지와 메일 전송 없음")
	void emailVerificationAPI_test14() throws Exception {
		Account owner = saveAccount("owner@example.com", "RSD3");
		Account other = saveAccount("other@example.com", "RSD4");
		EmailVerification previous = savePendingSensitiveAction(owner.getId(), "owner@example.com");
		
		resend(previous.getId(), accessToken(other.getId()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("VERIFICATION_NOT_FOUND"));
		
		assertPendingOnly(previous);
		verify(verificationMailSender, after(500).never()).send(any());
	}
	
	private ResultActions request(String email, String purpose) throws Exception {
		return mockMvc.perform(post("/api/v1/email-verifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"email": "%s", "purpose": "%s"}
						""".formatted(email, purpose)
				));
	}
	
	private ResultActions requestSensitiveAction(String body, String accessToken) throws Exception {
		MockHttpServletRequestBuilder builder = post("/api/v1/email-verifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
		
		if (accessToken != null) {
			builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		}
		
		return mockMvc.perform(builder);
	}
	
	private ResultActions resend(UUID verificationId, String accessToken) throws Exception {
		MockHttpServletRequestBuilder builder = post("/api/v1/email-verifications/" + verificationId + "/resend");
		
		if (accessToken != null) {
			builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		}
		
		return mockMvc.perform(builder);
	}
	
	private EmailVerification savePendingSensitiveAction(UUID accountId, String email) {
		return jpaEmailVerificationRepository.save(
				EmailVerification.request(
						accountId,
						Email.of(email),
						EmailVerificationPurpose.SENSITIVE_ACTION,
						tokenCrypto.hash("123456"),
						Instant.now().minus(Duration.ofMinutes(2))
				)
		);
	}
	
	private void assertPendingOnly(EmailVerification previous) {
		assertThat(jpaEmailVerificationRepository.count()).isEqualTo(1);
		
		EmailVerificationStatus previousStatus = jpaEmailVerificationRepository.findById(previous.getId())
				.orElseThrow().getStatus();
		
		assertThat(previousStatus).isEqualTo(EmailVerificationStatus.PENDING);
	}
	
	// 실제 발급기로 만든 Access Token
	private String accessToken(UUID accountId) {
		return accessTokenIssuer.issue(accountId, AccountRole.USER, AccountStatus.ACTIVE).accessToken();
	}
	
	private EmailVerification findVerification(ResultActions result) throws Exception {
		String body = result
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.expiresAt").exists())
				.andReturn()
				.getResponse()
				.getContentAsString();
		
		UUID verificationId = UUID.fromString(JsonPath.read(body, "$.data.verificationId"));
		
		return jpaEmailVerificationRepository.findById(verificationId).orElseThrow();
	}
	
	private VerificationMailRequested captureMail() {
		ArgumentCaptor<VerificationMailRequested> captor = ArgumentCaptor.forClass(VerificationMailRequested.class);
		
		verify(verificationMailSender, timeout(MAIL_WAIT_MILLIS)).send(captor.capture());
		
		return captor.getValue();
	}
	
	private Account saveAccount(String email, String tag) {
		Instant now = Instant.parse("2026-11-05T09:00:00Z");
		
		return accountRepository.save(
				Account.register(
						PublicIdentifier.of("티켓러", tag),
						Email.of(email),
						now,
						now
				)
		);
	}
}
