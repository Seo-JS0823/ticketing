package ticketing.account.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ticketing.account.account.domain.exception.AccountException;
import ticketing.account.account.domain.exception.AccountInvalidErrorCode;

public class EmailTest {

	@Test
	@DisplayName("이메일 소문자 정규화 테스트")
	void email_test1() {
		Email email = Email.of("User@example.com");
		
		assertThat(email.getEmail()).isEqualTo("user@example.com");
		assertThat(email).isEqualTo(Email.of("user@example.com"));
	}
	
	@Test
	@DisplayName("빈 값 예외")
	void email_test2() {
		assertThatThrownBy(() -> Email.of(" "))
				.isInstanceOf(AccountException.class)
				.extracting(e -> ((AccountException) e).getErrorCode())
				.isEqualTo(AccountInvalidErrorCode.EMAIL_REQUIRED);
	}
	
	@Test
	@DisplayName("형식이 틀리면 예외")
	void email_test3() {
		assertThatThrownBy(() -> Email.of("user.example.com"))
		.isInstanceOf(AccountException.class)
		.extracting(e -> ((AccountException) e).getErrorCode())
		.isEqualTo(AccountInvalidErrorCode.INVALID_EMAIL_PATTERN);
	}
}
