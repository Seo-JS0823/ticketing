package ticketing.account.account.infra.redis;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.port.VerificationSendLimiter;
import ticketing.account.account.application.util.VerificationCrypto;
import ticketing.account.account.domain.model.Email;
import ticketing.account.global.exception.RateLimitExceededException;

@Component
@RequiredArgsConstructor
public class RedisVerificationSendLimiter implements VerificationSendLimiter {

	private static final Duration WINDOW = Duration.ofHours(1);
	
	private static final long EMAIL_LIMIT = 5;
	
	private static final long IP_LIMIT = 20;
	
	private static final String KEY_PREFIX = "email-verification:send:";
	
	/*
	 * 횟수 증가와 만료 설정을 한 번에 실행
	 * 따로 실행시 그 사이에 실패했을 경우 만료 없는 키가 남아 영구 차단 가능
	 * return: {현재 횟수, 남은 만료 시간(ms)}
	 */
	@SuppressWarnings("rawtypes")
	private static final RedisScript<List> INCREMENT_SCRIPT = RedisScript.of(
			"""
			local count = redis.call('INCR', KEYS[1])
			if count == 1 then
				redis.call('PEXPIRE', KEYS[1], ARGV[1])
			end
			return {count, redis.call('PTTL', KEYS[1])}
			""",
			List.class
	);
	
	private final StringRedisTemplate redisTemplate;
	
	private final VerificationCrypto verificationCrypto;
	
	@Override
	public void acquire(Email email, String clientIp) {
		Window emailWindow = increment(KEY_PREFIX + "email:" + verificationCrypto.hash(email.getEmail()));
		
		Window ipWindow = increment(KEY_PREFIX + "ip:" + clientIp);
		
		long retryAfterMillis = 0;
		
		if (emailWindow.count() > EMAIL_LIMIT) {
			retryAfterMillis = emailWindow.ttlMillis();
		}
		
		if (ipWindow.count() > IP_LIMIT) {
			retryAfterMillis = Math.max(retryAfterMillis, ipWindow.ttlMillis());
		}
		
		if (retryAfterMillis > 0) {
			throw new RateLimitExceededException(Duration.ofMillis(retryAfterMillis));
		}
	}
	
	private Window increment(String key) {
		List<?> result = redisTemplate.execute(
				INCREMENT_SCRIPT,
				List.of(key),
				String.valueOf(WINDOW.toMillis())
		);
		
		return new Window(
				((Number) result.get(0)).longValue(),
				((Number) result.get(1)).longValue()
		);
	}
	
	private record Window(long count, long ttlMillis) {}
}
