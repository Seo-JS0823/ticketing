package ticketing.account.account.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.application.port.LoginAttemptLimiter;
import ticketing.account.account.application.util.VerificationCrypto;
import ticketing.account.global.exception.RateLimitExceededException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class RedisLoginAttemptLimiterTest {

	private static final String EMAIL = "user@example.com";
	
	private static final String IP = "10.0.0.1";
	
	@Autowired
	private LoginAttemptLimiter loginAttemptLimiter;
	
	@Autowired
	private StringRedisTemplate redisTemplate;
	
	@Autowired
	private RedisConnectionFactory redisConnectionFactory;
	
	@Autowired
	private VerificationCrypto verificationCrypto;
	
	@BeforeEach
	void clearRedis() {
		try (RedisConnection connection = redisConnectionFactory.getConnection()) {
			connection.serverCommands().flushDb();
		}
	}
	
	@Test
	@DisplayName("같은 이메일+IP로 4번 실패할 때까지는 허용")
	void loginAttemptLimiter_test1() {
		recordFailures(EMAIL, IP, 4);
		
		// 예외가 나지 않아야 한다.
		loginAttemptLimiter.checkAllowed(EMAIL, IP);
	}
	
	@Test
	@DisplayName("같은 이메일+IP로 5번 실패하면 429, Retry-After는 15분 이내")
	void loginAttemptLimiter_test2() {
		recordFailures(EMAIL, IP, 5);
		
		assertThatThrownBy(() -> loginAttemptLimiter.checkAllowed(EMAIL, IP))
				.isInstanceOf(RateLimitExceededException.class)
				.extracting(e -> ((RateLimitExceededException) e).getRetryAfterSeconds())
				.satisfies(seconds -> assertThat(seconds).isBetween(1L, 900L));
	}
	
	@Test
	@DisplayName("이메일+IP 한도는 IP마다 따로 센다")
	void loginAttemptLimiter_test3() {
		recordFailures(EMAIL, IP, 5);
		
		// 다른 IP는 아직 허용
		loginAttemptLimiter.checkAllowed(EMAIL, "10.0.0.2");
	}
	
	@Test
	@DisplayName("여러 IP에서 같은 이메일로 20번 실패하면 처음 보는 IP도 429")
	void loginAttemptLimiter_test4() {
		for (int i = 0; i < 20; i++) {
			// IP마다 1번씩 실패해서 이메일+IP 한도에는 걸리지 않게 한다.
			recordFailures(EMAIL, "10.0.1." + i, 1);
		}
		
		assertThatThrownBy(() -> loginAttemptLimiter.checkAllowed(EMAIL, "10.0.2.1"))
				.isInstanceOf(RateLimitExceededException.class);
	}
	
	@Test
	@DisplayName("로그인 성공 시 이메일+IP 카운터만 지우고 이메일 카운터는 유지")
	void loginAttemptLimiter_test5() {
		recordFailures(EMAIL, IP, 4);
		
		loginAttemptLimiter.recordSuccess(EMAIL, IP);
		
		assertThat(redisTemplate.hasKey(emailIpKey(EMAIL, IP))).isFalse();
		assertThat(redisTemplate.opsForValue().get(emailKey(EMAIL))).isEqualTo("4");
	}
	
	@Test
	@DisplayName("대소문자와 앞뒤 공백만 다른 이메일은 같은 카운터로 센다")
	void loginAttemptLimiter_test6() {
		recordFailures("User@Example.com", IP, 2);
		recordFailures("  user@example.com  ", IP, 2);
		recordFailures("USER@EXAMPLE.COM", IP, 1);
		
		assertThatThrownBy(() -> loginAttemptLimiter.checkAllowed(EMAIL, IP))
				.isInstanceOf(RateLimitExceededException.class);
	}
	
	@Test
	@DisplayName("형식이 잘못된 이메일과 빈 이메일도 실패로 센다, null과 공백은 같은 카운터")
	void loginAttemptLimiter_test7() {
		recordFailures("not-an-email", IP, 5);
		recordFailures(null, IP, 3);
		recordFailures("   ", IP, 2);
		
		assertThatThrownBy(() -> loginAttemptLimiter.checkAllowed("not-an-email", IP))
				.isInstanceOf(RateLimitExceededException.class);
		
		assertThatThrownBy(() -> loginAttemptLimiter.checkAllowed("", IP))
				.isInstanceOf(RateLimitExceededException.class);
	}
	
	@Test
	@DisplayName("실패 카운터는 15분 뒤 만료되고, 키에 이메일 원문이 남지 않는다")
	void loginAttemptLimiter_test8() {
		recordFailures(EMAIL, IP, 1);
		
		assertThat(redisTemplate.getExpire(emailIpKey(EMAIL, IP))).isBetween(899L, 900L);
		assertThat(redisTemplate.getExpire(emailKey(EMAIL))).isBetween(899L, 900L);
		assertThat(redisTemplate.keys("login-failure:*")).noneMatch(key -> key.contains(EMAIL));
	}
	
	private void recordFailures(String email, String ip, int times) {
		for (int i = 0; i < times; i++) {
			loginAttemptLimiter.recordFailure(email, ip);
		}
	}
	
	private String emailIpKey(String email, String ip) {
		return "login-failure:email-ip:" + verificationCrypto.hash(email) + ":" + ip;
	}
	
	private String emailKey(String email) {
		return "login-failure:email:" + verificationCrypto.hash(email);
	}
}