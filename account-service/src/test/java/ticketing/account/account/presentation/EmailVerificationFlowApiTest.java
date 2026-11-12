package ticketing.account.account.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import ticketing.account.account.application.dto.VerificationMailRequested;
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
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
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
	
	@Autowired
	private RedisConnectionFactory redisConnectionFactory;
	
	@BeforeEach
	void clearRedis() {
		try (RedisConnection connection = redisConnectionFactory.getConnection()) {
			connection.serverCommands().flushDb();
		}
	}
	
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
	
	@Test
	@DisplayName("이미 검증한 요청을 다시 검증하면 VERIFICATION_NOT_PENDING")
	void emailVerification_Flow_test2() throws Exception {
		UUID id = savePending("verify-twice@example.com", Instant.now());
		
		verifyCode(id, CODE).andExpect(status().isOk());
		
		verifyCode(id, CODE)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VERIFICATION_NOT_PENDING"));
	}
	
	@Test
	@DisplayName("틀린 코드는 INVALID_VERIFICATION_CODE 예외 및 실패 횟수 저장")
	void emailVerification_Flow_test3() throws Exception {
		UUID id = savePending("verify-wrong@example.com", Instant.now());
		
		verifyCode(id, WRONG_CODE)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
		
		assertThat(find(id).getFailedAttempts()).isEqualTo(1);
	}
	
	@Test
	@DisplayName("다섯 번 틀리면 CANCELLED 로 변경되고 맞는 코드도 거부")
	void emailVerification_Flow_test4() throws Exception {
		UUID id = savePending("verify-limit@example.com", Instant.now());
		
		for (int i = 0; i < 4; i++) {
			verifyCode(id, WRONG_CODE).andExpect(status().isBadRequest());
		}
		
		verifyCode(id, WRONG_CODE)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("VERIFICATION_ATTEMPTS_EXCEEDED"));
		
		assertThat(find(id).getFailedAttempts()).isEqualTo(5);
		
		verifyCode(id, CODE)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VERIFICATION_NOT_PENDING"));
	}
	
	@Test
	@DisplayName("동시에 틀린 코드를 보내도 실패 횟수를 정확히 카운트")
	void emailVerification_Flow_test5() throws Exception {
		UUID id = savePending("verify-race@example.com", Instant.now());
		
		int requests = 10;
		
		CountDownLatch start = new CountDownLatch(1);
		
		List<Future<Integer>> results = new ArrayList<>();
		
		try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
			for (int i = 0; i < requests; i++) {
				results.add(executor.submit(() -> {
					start.await();
					
					return verifyCode(id, WRONG_CODE).andReturn().getResponse().getStatus();
				}));
			}
			
			start.countDown();
			
			for (Future<Integer> result : results) {
				result.get();
			}
		}
		
		EmailVerification saved = find(id);
		
		assertThat(saved.getFailedAttempts()).isEqualTo(5);
		assertThat(saved.getStatus()).isEqualTo(EmailVerificationStatus.CANCELLED);
	}
	
	@Test
	@DisplayName("만료된 코드는 VERIFICATION_EXPIRED 예외 및 EXPIRED 저장")
	void emailVerification_Flow_test6() throws Exception {
		UUID id = savePending("verify-expired@example.com", Instant.now().minus(Duration.ofMinutes(11)));
		
		verifyCode(id, CODE)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
		
		assertThat(find(id).getStatus()).isEqualTo(EmailVerificationStatus.EXPIRED);
	}
	
	@Test
	@DisplayName("없는 요청을 검증하면 VERIFICATION_NOT_FOUND")
	void emailVerification_Flow_test7() throws Exception {
		verifyCode(UUID.randomUUID(), CODE)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("VERIFICATION_NOT_FOUND"));
	}
	
	@Test
	@DisplayName("코드가 없으면 INVALID_REQUEST")
	void emailVerification_Flow_test8() throws Exception {
		UUID id = savePending("verify-blank@example.com", Instant.now());
		
		mockMvc.perform(post(BASE_URL + "/" + id + "/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
	
	@Test
	@DisplayName("재발송하면 기존 요청은 CANCELLED로 변경 및 새 코드로 검증")
	void emailVerification_Flow_test9() throws Exception {
		UUID previousId = savePending("resend-ok@example.com", Instant.now().minus(Duration.ofMinutes(2)));
		
		String body = mockMvc.perform(post(BASE_URL + "/" + previousId + "/resend"))
				.andExpect(status().isAccepted())
				.andReturn()
				.getResponse()
				.getContentAsString();
		
		UUID newId = UUID.fromString(JsonPath.read(body, "$.data.verificationId"));
		
		assertThat(newId).isNotEqualTo(previousId);
		
		assertThat(find(previousId).getStatus()).isEqualTo(EmailVerificationStatus.CANCELLED);
		
		VerificationMailRequested mail = captureMail();
		
		assertThat(mail.verificationId()).isEqualTo(newId);
		verifyCode(newId, mail.code()).andExpect(status().isOk());
	}
	
	@Test
	@DisplayName("60초 안에 재발송하면 TOO_MANY_REQUESTS 예외 및 기존 요청은 유지")
	void emailVerification_Flow_test10() throws Exception {
		UUID id = savePending("resend-soon@example.com", Instant.now());
		
		mockMvc.perform(post(BASE_URL + "/" + id + "/resend"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
		
		assertThat(find(id).getStatus()).isEqualTo(EmailVerificationStatus.PENDING);
	}
	
	@Test
	@DisplayName("취소하면 204 및 다시 취소시 VERIFICATION_NOT_PENDING")
	void emailVerification_Flow_test11() throws Exception {
		UUID id = savePending("cancel@example.com", Instant.now());
		
		mockMvc.perform(delete(BASE_URL + "/" + id))
				.andExpect(status().isNoContent());
		
		assertThat(find(id).getStatus()).isEqualTo(EmailVerificationStatus.CANCELLED);
		
		mockMvc.perform(delete(BASE_URL + "/" + id))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VERIFICATION_NOT_PENDING"));
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
	
	private VerificationMailRequested captureMail() {
		ArgumentCaptor<VerificationMailRequested> captor = ArgumentCaptor.forClass(VerificationMailRequested.class);
		
		verify(verificationMailSender, timeout(MAIL_WAIT_MILLIS)).send(captor.capture());
		
		return captor.getValue();
	}
}
