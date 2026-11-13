package ticketing.account.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ticketing.account.account.domain.exception.EmailVerificationErrorCode;
import ticketing.account.account.domain.exception.EmailVerificationException;

public class EmailVerificationConsumeTest {

	private static final Instant NOW = Instant.parse("2026-11-05T09:00:00Z");
	
	private static final String CODE_HASH = "code-hash";
	
	@Test
	@DisplayName("검증된 토큰은 1회만 사용 가능")
	void consume_test1() {
		EmailVerification verification = verified();
		
		verification.consume(EmailVerificationPurpose.SIGN_UP, NOW.plusSeconds(60));
		
		assertThat(verification.getConsumedAt()).isEqualTo(NOW.plusSeconds(60));
	}
	
	@Test
	@DisplayName("이미 사용한 토큰은 거부")
	void consume_test2() {
		EmailVerification verification = verified();
		
		verification.consume(EmailVerificationPurpose.SIGN_UP, NOW);
		
		assertInvalidToken(() -> verification.consume(EmailVerificationPurpose.SIGN_UP, NOW));
	}
	
	@Test
	@DisplayName("인증 목적이 다르면 거부")
	void consume_test3() {
		EmailVerification verification = verified();
		
		assertInvalidToken(() -> verification.consume(EmailVerificationPurpose.PASSWORD_RESET, NOW));
		
		assertThat(verification.getConsumedAt()).isNull();
	}
	
	@Test
	@DisplayName("토큰 유효시간 10분이 지나면 거부")
	void consume_test4() {
		EmailVerification verification = verified();
		
		assertInvalidToken(() -> verification.consume(EmailVerificationPurpose.SIGN_UP, NOW.plusSeconds(600)));
	}
	
	@Test
	@DisplayName("검증하지 않은 요청은 거부")
	void consume_test5() {
		EmailVerification verification = pending();
		
		assertInvalidToken(() -> verification.consume(EmailVerificationPurpose.SIGN_UP, NOW));
	}
	
	private EmailVerification pending() {
		return EmailVerification.request(
				null,
				Email.of("user@example.com"),
				EmailVerificationPurpose.SIGN_UP,
				CODE_HASH,
				NOW
		);
	}
	
	private EmailVerification verified() {
		EmailVerification verification = pending();
		
		verification.verify(CODE_HASH, "token-hash", NOW);
		
		return verification;
	}
	
	private void assertInvalidToken(ThrowingCallable action) {
		assertThatThrownBy(action)
				.isInstanceOf(EmailVerificationException.class)
				.extracting(e -> ((EmailVerificationException) e).getErrorCode())
				.isEqualTo(EmailVerificationErrorCode.INVALID_VERIFICATION_TOKEN);
	}
}
