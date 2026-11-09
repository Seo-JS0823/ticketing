package ticketing.account.account.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.utility.TestcontainersConfiguration;

import com.jayway.jsonpath.JsonPath;

import ticketing.account.account.application.VerificationCrypto;
import ticketing.account.account.application.VerificationMailRequested;
import ticketing.account.account.application.VerificationMailSender;
import ticketing.account.account.application.VerificationMailType;
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
