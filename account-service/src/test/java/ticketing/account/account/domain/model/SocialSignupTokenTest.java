package ticketing.account.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ticketing.account.account.domain.exception.AuthErrorCode;
import ticketing.account.account.domain.exception.AuthException;

public class SocialSignupTokenTest {

	private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");
	
	@Test
	@DisplayName("발급하면 소셜 사용자 정보를 담고 30분 뒤 만료")
	void socialSignupToken_test1() {
		SocialSignupToken token = issue("kakao@example.com");
		
		assertThat(token.getProvider()).isEqualTo(SocialProvider.KAKAO);
		assertThat(token.getProviderUserId()).isEqualTo("1234567890");
		assertThat(token.getProviderEmail()).isEqualTo("kakao@example.com");
		assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
		assertThat(token.getConsumedAt()).isNull();
	}
	
	@Test
	@DisplayName("소셜 이메일을 받지 못해도 발급 가능")
	void socialSignupToken_test2() {
		SocialSignupToken token = issue(null);
		
		assertThat(token.getProviderEmail()).isNull();
	}
	
	@Test
	@DisplayName("사용하면 사용 시각 기록")
	void socialSignupToken_test3() {
		SocialSignupToken token = issue("kakao@example.com");
		Instant later = NOW.plus(Duration.ofMinutes(5));
		
		token.consume(later);
		
		assertThat(token.getConsumedAt()).isEqualTo(later);
	}
	
	@Test
	@DisplayName("이미 사용한 토큰은 다시 사용 불가")
	void socialSignupToken_test4() {
		SocialSignupToken token = issue("kakao@example.com");
		token.consume(NOW);
		
		assertInvalidSignupToken(() -> token.consume(NOW));
	}
	
	@Test
	@DisplayName("30분이 지나면 사용 불가")
	void socialSignupToken_test5() {
		SocialSignupToken token = issue("kakao@example.com");
		
		assertInvalidSignupToken(() -> token.consume(NOW.plus(Duration.ofMinutes(30))));
	}
	
	private SocialSignupToken issue(String providerEmail) {
		return SocialSignupToken.issue(SocialProvider.KAKAO, "1234567890", providerEmail, "hash-1", NOW);
	}
	
	private void assertInvalidSignupToken(ThrowingCallable callable) {
		assertThatThrownBy(callable)
				.isInstanceOf(AuthException.class)
				.extracting(e -> ((AuthException) e).getErrorCode())
				.isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN);
	}
}