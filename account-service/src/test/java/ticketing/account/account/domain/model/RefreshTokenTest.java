package ticketing.account.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ticketing.account.account.domain.exception.AuthErrorCode;
import ticketing.account.account.domain.exception.AuthException;

public class RefreshTokenTest {

	private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");
	
	private static final UUID ACCOUNT_ID = UUID.randomUUID();
	
	@Test
	@DisplayName("발급하면 새 세션을 만들고 14일 뒤 만료")
	void refreshToken_test1() {
		RefreshToken token = RefreshToken.issue(ACCOUNT_ID, "hash-1", NOW);
		
		assertThat(token.getSessionId()).isNotNull();
		assertThat(token.getAccountId()).isEqualTo(ACCOUNT_ID);
		
		assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
		assertThat(token.isUsed()).isFalse();
	}
	
	@Test
	@DisplayName("재발급하면 이 토큰은 사용 처리하고 같은 세션의 새 토큰 생성")
	void refreshToken_test2() {
		RefreshToken token = RefreshToken.issue(ACCOUNT_ID, "hash-1", NOW);
		
		Instant later = NOW.plus(Duration.ofMinutes(15));
		
		RefreshToken next = token.rotate("hash-2", later);
		
		assertThat(token.getUsedAt()).isEqualTo(later);
		
		assertThat(next.getSessionId()).isEqualTo(token.getSessionId());
		assertThat(next.getAccountId()).isEqualTo(ACCOUNT_ID);
		assertThat(next.getTokenHash()).isEqualTo("hash-2");
		
		assertThat(next.getExpiresAt()).isEqualTo(later.plus(Duration.ofDays(14)));
	}
	
	@Test
	@DisplayName("이미 사용한 토큰은 재발급 불가")
	void refreshToken_test3() {
		RefreshToken token = RefreshToken.issue(ACCOUNT_ID, "hash-1", NOW);
		
		token.rotate("hash-2", NOW);
		
		assertInvalidRefreshToken(() -> token.rotate("hash-3", NOW));
	}
	
	@Test
	@DisplayName("14일이 지나면 재발급 불가")
	void refreshToken_test4() {
		RefreshToken token = RefreshToken.issue(ACCOUNT_ID, "hash-1", NOW);
		
		assertInvalidRefreshToken(() -> token.rotate("hash-2", NOW.plus(Duration.ofDays(14))));
	}
	
	private void assertInvalidRefreshToken(ThrowingCallable callable) {
		assertThatThrownBy(callable)
				.isInstanceOf(AuthException.class)
				.extracting(e -> ((AuthException) e).getErrorCode())
				.isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
	}
}
