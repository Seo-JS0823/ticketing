package ticketing.account.account.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.application.VerificationMailType;
import ticketing.account.account.application.dto.VerificationMailRequested;
import ticketing.account.account.application.port.VerificationMailSender;
import ticketing.account.account.application.util.VerificationCrypto;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.EmailVerification;
import ticketing.account.account.domain.model.EmailVerificationStatus;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.account.infra.persistence.JpaEmailVerificationRepository;

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
	private VerificationCrypto verificationCrypto;
	
	@MockitoBean
	private VerificationMailSender verificationMailSender;
	
	@Autowired
	private RedisConnectionFactory redisConnectionFactory;
	
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
		assertThat(saved.getCodeHash()).isEqualTo(verificationCrypto.hash(mail.code()));
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
	
	private ResultActions request(String email, String purpose) throws Exception {
		return mockMvc.perform(post("/api/v1/email-verifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"email": "%s", "purpose": "%s"}
						""".formatted(email, purpose)
				));
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
