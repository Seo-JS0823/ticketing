package ticketing.account.account.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ticketing.account.TestcontainersConfiguration;
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
public class EmailVerificationRateLimitApiTest {

	private static final String BASE_URL = "/api/v1/email-verifications";
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private JpaEmailVerificationRepository jpaEmailVerificationRepository;
	
	@Autowired
	private VerificationCrypto verificationCrypto;
	
	@Autowired
	private StringRedisTemplate redisTemplate;
	
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
	@DisplayName("같은 이메일은 시간당 5회까지 요청 6번째 요청은 Retry_Ater 헤더와 함께 거부")
	void emailVerification_RateLimit_test1() throws Exception {
		for (int i = 0; i < 5; i++) {
			request("limit@example.com", "10.0.0.1")
					.andExpect(status().isAccepted());
		}
		
		String retryAfter = request("limit@example.com", "10.0.0.2")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
				.andExpect(header().exists("Retry-After"))
				.andReturn()
				.getResponse()
				.getHeader("Retry-After");
		
		assertThat(Long.parseLong(retryAfter)).isBetween(1L, 3600L);
	}
	
	@Test
	@DisplayName("같은 IP는 시간당 20회까지 요청 가능")
	void emailVerification_RateLimit_test2() throws Exception {
		for (int i = 0; i < 20; i++) {
			request("ip-" + i + "@example.com", "10.0.0.3")
					.andExpect(status().isAccepted());
		}
		
		request("ip-20@example.com", "10.0.0.3")
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"));
		
		request("ip-20@example.com", "10.0.0.4")
				.andExpect(status().isAccepted());
	}
	
	@Test
	@DisplayName("메일을 보내지 않는 요청도 한도에 포함")
	void emailVerification_RateLimit_test3() throws Exception {
		for (int i = 0; i < 5; i++) {
			request("nobody@example.com", "PASSWORD_RESET", "10.0.0.5")
					.andExpect(status().isAccepted());
		}
		
		request("nobody@example.com", "PASSWORD_RESET", "10.0.0.5")
				.andExpect(status().isTooManyRequests());
	}
	
	@Test
	@DisplayName("재발송을 60초안에 하면 남은 시간을 Retry_After로 알린다")
	void emailVerification_RateLimit_test4() throws Exception {
		UUID id = savePending("resend-soon@example.com", Instant.now().minusSeconds(10));
		
		String retryAfter = mockMvc.perform(post(BASE_URL + "/" + id + "/resend"))
				.andExpect(status().isTooManyRequests())
				.andReturn()
				.getResponse()
				.getHeader("Retry-After");
		
		assertThat(Long.parseLong(retryAfter)).isBetween(1L, 50L);
	}
	
	@Test
	@DisplayName("새로 요청시 같은 이메일과 같은 목적의 대기중인 요청은 취소")
	void emailVerification_RateLimit_test5() throws Exception {
		UUID first = verificationId(request("again@example.com", "10.0.0.6"));
		UUID second = verificationId(request("again@example.com", "10.0.0.6"));
		
		assertThat(find(first).getStatus()).isEqualTo(EmailVerificationStatus.CANCELLED);
		assertThat(find(second).getStatus()).isEqualTo(EmailVerificationStatus.PENDING);
	}
	
	@Test
	@DisplayName("Redis 키에 이메일 원문을 남기지 않음")
	void emailVerification_RateLimit_test6() throws Exception {
		request("private@example.com", "10.0.0.7").andExpect(status().isAccepted());
		
		Set<String> keys = redisTemplate.keys("email-verification:send:*");
		
		assertThat(keys).isNotEmpty();
		assertThat(keys).noneMatch(key -> key.contains("private@example.com"));
		assertThat(keys).allMatch(key -> redisTemplate.getExpire(key) > 0);
	}
	
	private ResultActions request(String email, String clientIp) throws Exception {
		return request(email, "SIGN_UP", clientIp);
	}
	
	private ResultActions request(String email, String purpose, String clientIp) throws Exception {
		return mockMvc.perform(post(BASE_URL)
				.with(servletRequest -> {
					servletRequest.setRemoteAddr(clientIp);
					return servletRequest;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"email":"%s","purpose":"%s"}
						""".formatted(email, purpose)
				));
	}
	
	private UUID verificationId(ResultActions result) throws Exception {
		String body = result.andExpect(status().isAccepted())
				.andReturn()
				.getResponse()
				.getContentAsString();
		
		return UUID.fromString(JsonPath.read(body, "$.data.verificationId"));
	}
	
	private UUID savePending(String email, Instant requestedAt) {
		return jpaEmailVerificationRepository.save(
				EmailVerification.request(
						null,
						Email.of(email),
						EmailVerificationPurpose.SIGN_UP,
						verificationCrypto.hash("123456"),
						requestedAt
				))
				.getId();
	}
	
	private EmailVerification find(UUID id) {
		return jpaEmailVerificationRepository.findById(id).orElseThrow();
	}
}
