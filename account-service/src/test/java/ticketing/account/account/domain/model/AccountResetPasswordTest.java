package ticketing.account.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ticketing.account.account.domain.exception.AccountErrorCode;
import ticketing.account.account.domain.exception.AccountException;

public class AccountResetPasswordTest {

	private static final Instant CREATED_AT = Instant.parse("2026-11-01T00:00:00Z");
	
	private static final Instant RESET_AT = Instant.parse("2026-11-16T00:00:00Z");
	
	@Test
	@DisplayName("비밀번호가 있는 계정은 새 비밀번호로 변경")
	void accountResetPassword_test1() {
		Account account = newAccount();
		account.registerPassword("old-hash", CREATED_AT);
		
		account.resetPassword("new-hash", RESET_AT);
		
		assertThat(account.getCredential().getPasswordHash()).isEqualTo("new-hash");
		assertThat(account.getCredential().getPasswordChangedAt()).isEqualTo(RESET_AT);
		assertThat(account.getUpdatedAt()).isEqualTo(RESET_AT);
	}
	
	@Test
	@DisplayName("소셜 전용 계정(비밀번호 없음)은 비밀번호를 새로 등록")
	void accountResetPassword_test2() {
		Account account = newAccount();
		
		account.resetPassword("new-hash", RESET_AT);
		
		assertThat(account.getCredential()).isNotNull();
		assertThat(account.getCredential().getPasswordHash()).isEqualTo("new-hash");
		assertThat(account.getUpdatedAt()).isEqualTo(RESET_AT);
	}
	
	@Test
	@DisplayName("탈퇴 계정은 ACCOUNT_NOT_ACTIVE로 거부하고 비밀번호를 바꾸지 않음")
	void accountResetPassword_test3() {
		Account account = newAccount();
		account.registerPassword("old-hash", CREATED_AT);
		
		// 탈퇴 기능이 아직 없어 상태 필드를 직접 바꾼다
		ReflectionTestUtils.setField(account, "status", AccountStatus.WITHDRAWN);
		
		assertThatThrownBy(() -> account.resetPassword("new-hash", RESET_AT))
				.isInstanceOf(AccountException.class)
				.extracting(e -> ((AccountException) e).getErrorCode())
				.isEqualTo(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
		
		assertThat(account.getCredential().getPasswordHash()).isEqualTo("old-hash");
	}
	
	private Account newAccount() {
		return Account.register(
				PublicIdentifier.of("재설정회원", "RESET001"),
				Email.of("reset@example.com"),
				CREATED_AT,
				CREATED_AT
		);
	}
}