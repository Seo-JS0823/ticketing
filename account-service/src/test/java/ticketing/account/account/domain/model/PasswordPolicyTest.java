package ticketing.account.account.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import ticketing.account.account.domain.exception.AccountException;
import ticketing.account.account.domain.exception.AccountInvalidErrorCode;

public class PasswordPolicyTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"abcd1234!",						// 8자
			"Ab1!@#$%^&*",					// 허용 특수문자 전부
			"Aa1!Aa1!Aa1!Aa1!Aa1!"	// 20자
	})
	@DisplayName("규칙을 지킨 비밀번호 허용")
	void passwordPolicy_test1(String password) {
		assertThatCode(() -> PasswordPolicy.validate(password))
				.doesNotThrowAnyException();
	}
	
	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			"abc123!",								// 7자
			"Aa1!Aa1!Aa1!Aa1!Aa1!A",	// 21자
			"abcd1234",								// 특수문자 없음
			"abcd!@#$",								// 숫자 없음
			"1234!@#$",								// 영문 없음
			"abcd123?",								// 허용하지 않는 특수문자
			"abcd 123!",							// 공백
			"ㅍㅗㅇㅣ123!"						// 한글
	})
	@DisplayName("규칙을 벗어난 비밀번호 거부")
	void passwordPolicy_test2(String password) {
		assertThatThrownBy(() -> PasswordPolicy.validate(password))
				.isInstanceOf(AccountException.class)
				.extracting(e -> ((AccountException) e).getErrorCode())
				.isEqualTo(AccountInvalidErrorCode.INVALID_PASSWORD_PATTERN);
	}
	
}
