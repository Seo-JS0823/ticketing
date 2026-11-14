package ticketing.account.account.infra.redis;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.port.LoginAttemptLimiter;
import ticketing.account.account.application.util.VerificationCrypto;
import ticketing.account.global.exception.RateLimitExceededException;

@Component
@RequiredArgsConstructor
public class RedisLoginAttemptLimiter implements LoginAttemptLimiter {

	private static final Duration WINDOW = Duration.ofMinutes(15);
	
	private static final long EMAIL_IP_LIMIT = 5;
	
	private static final long EMAIL_LIMIT = 20;
	
	private static final String KEY_PREFIX = "login-failure:";
	
	/*
	 * 횟수 증가와 만료 설정을 한 번에 실행 (RedisVerificationSendLimiter와 같은 스크립트)
	 * 따로 실행하면 그 사이에 실패했을 때 만료 없는 키가 남아 영구 차단될 수 있다.
	 */
	private static final RedisScript<Long> INCREMENT_SCRIPT = RedisScript.of(
			"""
			local count = redis.call('INCR', KEYS[1])
			if count == 1 then
				redis.call('PEXPIRE', KEYS[1], ARGV[1])
			end
			return count
			""",
			Long.class
	);
	
	private final StringRedisTemplate redisTemplate;
	
	private final VerificationCrypto verificationCrypto;
	
	@Override
	public void checkAllowed(String email, String clientIp) {
		String emailHash = hashEmail(email);
		
		long retryAfterMillis = Math.max(
				retryAfterMillis(emailIpKey(emailHash, clientIp), EMAIL_IP_LIMIT),
				retryAfterMillis(emailKey(emailHash), EMAIL_LIMIT)
		);
		
		if (retryAfterMillis > 0) {
			throw new RateLimitExceededException(Duration.ofMillis(retryAfterMillis));
		}
	}
	
	@Override
	public void recordFailure(String email, String clientIp) {
		String emailHash = hashEmail(email);
		
		increment(emailIpKey(emailHash, clientIp));
		increment(emailKey(emailHash));
	}
	
	@Override
	public void recordSuccess(String email, String clientIp) {
		redisTemplate.delete(emailIpKey(hashEmail(email), clientIp));
	}
	
	/*
	 * 한도에 도달했으면 남은 시간(ms), 아니면 0
	 * 횟수를 늘리지 않고 읽기만 한다.
	 */
	private long retryAfterMillis(String key, long limit) {
		String count = redisTemplate.opsForValue().get(key);
		
		if (count == null || Long.parseLong(count) < limit) {
			return 0;
		}
		
		// 읽는 사이에 만료되면 -2가 오므로 0으로 본다.
		Long ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
		
		return ttlMillis == null ? 0 : Math.max(ttlMillis, 0);
	}
	
	private void increment(String key) {
		redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(WINDOW.toMillis()));
	}
	
	/*
	 * 앞뒤 공백 제거 + 소문자로 바꾼 뒤 HMAC
	 * 형식이 잘못된 이메일도 같은 규칙으로 키를 만들고, null은 빈 문자열로 본다.
	 * 이메일 원문은 Redis에 남기지 않는다.
	 */
	private String hashEmail(String email) {
		String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
		
		return verificationCrypto.hash(normalized);
	}
	
	private String emailIpKey(String emailHash, String clientIp) {
		return KEY_PREFIX + "email-ip:" + emailHash + ":" + clientIp;
	}
	
	private String emailKey(String emailHash) {
		return KEY_PREFIX + "email:" + emailHash;
	}
}