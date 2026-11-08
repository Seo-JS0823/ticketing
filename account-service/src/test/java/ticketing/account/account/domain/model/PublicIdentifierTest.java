package ticketing.account.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ticketing.account.account.domain.exception.AccountException;
import ticketing.account.account.domain.exception.AccountInvalidErrorCode;

public class PublicIdentifierTest {

	@ParameterizedTest
	@ValueSource(strings = {"티켓", "ab", "티켓러Ticket1"})
	@DisplayName("닉네임은 2자에서 10자까지 허용")
	void publicIdentifier_test1(String nickname) {
		assertThatCode(() -> PublicIdentifier.of(nickname, "ABCD"))
				.doesNotThrowAnyException();
	}
	
	@ParameterizedTest
	@ValueSource(strings = {"a", "티켓러Ticket123", "티켓 러", "ㅋ ㅋ ㅋ "})
	@DisplayName("규칙을 벗어난 닉네임은 예외")
	void publicIdentifier_test2(String nickname) {
		assertThatThrownBy(() -> PublicIdentifier.of(nickname, "ABCD"))
				.isInstanceOf(AccountException.class)
				.extracting(e -> ((AccountException) e).getErrorCode())
				.isEqualTo(AccountInvalidErrorCode.INVALID_NICKNAME_PATTERN);
	}
	
	@ParameterizedTest
	@ValueSource(strings = {"태그변경", "ABCD", "1234", "티켓A1", "A1B2C3D4"})
	@DisplayName("태그는 한글, 영문, 숫자 4자에서 8자까지 허용")
	void publicIdentifier_test3(String tag) {
		assertThatCode(() -> PublicIdentifier.of("티켓러", tag))
				.doesNotThrowAnyException();
	}
	
	@ParameterizedTest
	@ValueSource(strings = {"ABC", "A1B2C3D4E", "AB-1"})
	@DisplayName("규칙을 벗어난 태그는 예외")
	void PublicIdentifier_test4(String tag) {
		assertThatThrownBy(() -> PublicIdentifier.of("티켓러", tag))
				.isInstanceOf(AccountException.class)
				.extracting(e -> ((AccountException) e).getErrorCode())
				.isEqualTo(AccountInvalidErrorCode.INVALID_TAG_PATTERN);
	}
	
	@Test
	@DisplayName("자모가 분리된 한글도 NFC로 정규화해 허용")
	void PublicIdentifier_test5() {
		String decomposed = Normalizer.normalize("티켓러", Normalizer.Form.NFD);
		
		PublicIdentifier identifier = PublicIdentifier.of(decomposed, "ABCD");
		
		assertThat(identifier.getNickname()).isEqualTo("티켓러");
	}
	
	@Test
	@DisplayName("생성된 태그는 대문자와 숫자 8자리")
	void PublicIdentifier_test6() {
		PublicIdentifier identifier = PublicIdentifier.withGeneratedTag("티켓러", new Random(1));
		
		assertThat(identifier.getTag()).matches("^[A-Z0-9]{8}$");
	}
}
