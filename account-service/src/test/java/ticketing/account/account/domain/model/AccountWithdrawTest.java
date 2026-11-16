package ticketing.account.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ticketing.account.account.domain.exception.AccountErrorCode;
import ticketing.account.account.domain.exception.AccountException;

public class AccountWithdrawTest {

	private static final Instant CREATED_AT = Instant.parse("2026-11-01T00:00:00Z");
	
	private static final Instant WITHDRAWN_AT = Instant.parse("2026-11-17T09:30:00Z");
	
	private static final Duration RECOVERY_PERIOD = Duration.ofDays(30);
	
	@Test
	@DisplayName("탈퇴하면 WITHDRAWN, 탈퇴 시각과 복구 기한(탈퇴 시각 + 기간) 기록, 비밀번호와 소셜 연결은 유지")
	void accountWithdraw_test1() {
		Account account = newAccount();
		account.registerPassword("password-hash", CREATED_AT);
		account.connectSocialAccount(SocialProvider.KAKAO, "kakao-1", CREATED_AT);
		
		account.withdraw(WITHDRAWN_AT, RECOVERY_PERIOD);
		
		assertThat(account.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
		assertThat(account.getWithdrawnAt()).isEqualTo(WITHDRAWN_AT);
		assertThat(account.getRecoveryExpiresAt()).isEqualTo(Instant.parse("2026-12-17T09:30:00Z"));
		assertThat(account.getUpdatedAt()).isEqualTo(WITHDRAWN_AT);
		assertThat(account.getCredential().getPasswordHash()).isEqualTo("password-hash");
		assertThat(account.getActiveSocialAccounts()).hasSize(1);
	}
	
	@Test
	@DisplayName("정지(SUSPENDED) 계정도 탈퇴할 수 있음")
	void accountWithdraw_test2() {
		Account account = newAccount();
		
		// 제재 기능이 아직 없어 상태 필드를 직접 바꾼다
		ReflectionTestUtils.setField(account, "status", AccountStatus.SUSPENDED);
		
		account.withdraw(WITHDRAWN_AT, RECOVERY_PERIOD);
		
		assertThat(account.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
	}
	
	@Test
	@DisplayName("이미 탈퇴한 계정은 ACCOUNT_NOT_ACTIVE, 처음 탈퇴 시각과 복구 기한은 그대로")
	void accountWithdraw_test3() {
		Account account = newAccount();
		account.withdraw(WITHDRAWN_AT, RECOVERY_PERIOD);
		
		Instant later = WITHDRAWN_AT.plus(Duration.ofDays(1));
		
		assertThatThrownBy(() -> account.withdraw(later, RECOVERY_PERIOD))
				.isInstanceOf(AccountException.class)
				.extracting(e -> ((AccountException) e).getErrorCode())
				.isEqualTo(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
		
		assertThat(account.getWithdrawnAt()).isEqualTo(WITHDRAWN_AT);
		assertThat(account.getRecoveryExpiresAt()).isEqualTo(Instant.parse("2026-12-17T09:30:00Z"));
	}
	
	private Account newAccount() {
		return Account.register(
				PublicIdentifier.of("탈퇴회원", "WDRW0001"),
				Email.of("withdraw@example.com"),
				CREATED_AT,
				CREATED_AT
		);
	}
}