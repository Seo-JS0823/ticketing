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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.utility.TestcontainersConfiguration;

import com.jayway.jsonpath.JsonPath;

import ticketing.account.account.application.port.VerificationMailSender;
import ticketing.account.account.application.util.VerificationCrypto;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.EmailVerification;
import ticketing.account.account.domain.model.EmailVerificationPurpose;
import ticketing.account.account.domain.model.EmailVerificationStatus;
import ticketing.account.account.infra.persistence.JpaEmailVerificationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public class EmailVerificationFlowApiTest {

	private static final String BASE_URL = "/api/v1/email-verifications";
	
	private static final String CODE = "123456";
	
	private static final String WRONG_CODE = "000000";
	
	private static final long MAIL_WAIT_MILLIS = 5000;
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private JpaEmailVerificationRepository jpaEmailVerificationRepository;
	
	@Autowired
	private VerificationCrypto verificationCrypto;
	
	@MockitoBean
	private VerificationMailSender verificationMailSender;
	
	@Test
	@DisplayName("맞는 코드로 검증하면 토큰을 발급하고 해시만 저장")
	void emailVerification_Flow_test1() throws Exception {
		UUID id = savePending("verify-ok@example.com", Instant.now());
		
		String body = verifyCode(id, CODE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.expiresAt").exists())
				.andReturn()
				.getResponse()
				.getContentAsString();
		
		String token = JsonPath.read(body, "$.data.verificationToken");
		
		EmailVerification saved = find(id);
		
		assertThat(saved.getStatus()).isEqualTo(EmailVerificationStatus.VERIFIED);
		assertThat(saved.getTokenHash()).isEqualTo(verificationCrypto.hash(token));
		assertThat(saved.getTokenHash()).isNotEqualTo(token);
	}
	
	private ResultActions verifyCode(UUID id, String code) throws Exception {
		return mockMvc.perform(post(BASE_URL + "/" + id + "/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"code":"%s"}
						""".formatted(code)
				));
	}
	
	private UUID savePending(String email, Instant requestedAt) {
		return jpaEmailVerificationRepository.save(
				EmailVerification.request(
						null,
						Email.of(email),
						EmailVerificationPurpose.SIGN_UP,
						verificationCrypto.hash(CODE),
						requestedAt
				)
		).getId();
	}
	
	private EmailVerification find(UUID id) {
		return jpaEmailVerificationRepository.findById(id).orElseThrow();
	}
}
