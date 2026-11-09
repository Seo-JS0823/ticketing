package ticketing.account.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ticketing.account.account.domain.exception.EmailVerificationErrorCode;
import ticketing.account.account.domain.exception.EmailVerificationException;
import ticketing.account.global.exception.CommonErrorCode;
import ticketing.account.global.exception.DomainErrorCode;

public class EmailVerificationTest {

	private static final Instant NOW = Instant.parse("2026-11-05T09:00:00Z");

	private static final String CODE_HASH = "code-hash";

	private static final String WRONG_HASH = "wrong-hash";

	private static final String TOKEN_HASH = "token-hash";

	@Test
	@DisplayName("요청시 PENDING 상태 10분뒤 만료")
	void emailVerification_test1() {
		EmailVerification verification = newVerification();
		
		assertThat(verification.getStatus()).isEqualTo(EmailVerificationStatus.PENDING);
		assertThat(verification.getFailedAttempts()).isZero();
		assertThat(verification.getExpiresAt()).isEqualTo(NOW.plusSeconds(600));
	}
	
	@Test
	@DisplayName("코드가 맞으면 VERIFIED가 되고 토큰 정보를 저장")
	void emailVerification_test2() {
		EmailVerification verification = newVerification();
		
		verification.verify(CODE_HASH, TOKEN_HASH, NOW.plusSeconds(60));
		
		assertThat(verification.getStatus()).isEqualTo(EmailVerificationStatus.VERIFIED);
		assertThat(verification.getVerifiedAt()).isEqualTo(NOW.plusSeconds(60));
		assertThat(verification.getTokenHash()).isEqualTo(TOKEN_HASH);
		assertThat(verification.getTokenExpiresAt()).isEqualTo(NOW.plusSeconds(660));
	}
	
	@Test
	@DisplayName("코드가 틀리면 실패 횟수가 증가하고 PENDING 상태 유지")
	void emailVerification_test3() {
		EmailVerification verification = newVerification();
		
		assertErrorCode(
				() -> verification.verify(WRONG_HASH, TOKEN_HASH, NOW),
				EmailVerificationErrorCode.INVALID_VERIFICATION_CODE
		);
		
		assertThat(verification.getFailedAttempts()).isEqualTo(1);
		assertThat(verification.getStatus()).isEqualTo(EmailVerificationStatus.PENDING);
	}
	
	@Test
	@DisplayName("다섯 번째 실패에서 CANCELLED 상태 전환")
	void emailVerification_test4() {
		EmailVerification verification = newVerification();
		
		for (int i = 0; i < 4; i++) {
			assertErrorCode(
					() -> verification.verify(WRONG_HASH, TOKEN_HASH, NOW),
					EmailVerificationErrorCode.INVALID_VERIFICATION_CODE
			);
		}
		
		assertErrorCode(
				() -> verification.verify(WRONG_HASH, TOKEN_HASH, NOW),
				EmailVerificationErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED
		);
		
		assertThat(verification.getStatus()).isEqualTo(EmailVerificationStatus.CANCELLED);
	}
	
	@Test
	@DisplayName("만료 시각이 되면 EXPIRED 상태 전환")
	void emailVerification_test5() {
		EmailVerification verification = newVerification();

		assertErrorCode(
				() -> verification.verify(CODE_HASH, TOKEN_HASH, NOW.plusSeconds(600)),
				EmailVerificationErrorCode.VERIFICATION_EXPIRED
		);
		
		assertThat(verification.getStatus()).isEqualTo(EmailVerificationStatus.EXPIRED);
	}
	
	@Test
	@DisplayName("이미 검증한 요청은 다시 검증할 수 없음")
	void emailVerification_test6() {
		EmailVerification verification = newVerification();
		
		verification.verify(CODE_HASH, TOKEN_HASH, NOW);
		
		assertErrorCode(
				() -> verification.verify(CODE_HASH, TOKEN_HASH, NOW),
				EmailVerificationErrorCode.VERIFICATION_NOT_PENDING
		);
	}
	
	@Test
	@DisplayName("재발송은 60초가 지나야 가능")
	void emailVerification_test7() {
		EmailVerification verification = newVerification();
		
		assertErrorCode(
				() -> verification.cancelForResend(NOW.plusSeconds(59)),
				CommonErrorCode.TOO_MANY_REQUESTS
		);
		
		assertThatCode(() -> verification.cancelForResend(NOW.plusSeconds(60)))
				.doesNotThrowAnyException();
		
		assertThat(verification.getStatus()).isEqualTo(EmailVerificationStatus.CANCELLED);
	}
	
	@Test
	@DisplayName("대기 중인 요청을 취소할 수 있음")
	void emailVerification_test8() {
		EmailVerification verification = newVerification();
		
		verification.cancel();
		
		assertThat(verification.getStatus()).isEqualTo(EmailVerificationStatus.CANCELLED);
	}
	
	private EmailVerification newVerification() {
		return EmailVerification.request(
				null,
				Email.of("user@example.com"),
				EmailVerificationPurpose.SIGN_UP,
				CODE_HASH,
				NOW);
	}
	
	private void assertErrorCode(ThrowingCallable action, DomainErrorCode expected) {
		assertThatThrownBy(action)
				.isInstanceOf(EmailVerificationException.class)
				.extracting(e -> ((EmailVerificationException) e).getErrorCode())
				.isEqualTo(expected);
	}
}
