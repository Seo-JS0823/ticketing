package ticketing.account.account.domain.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.account.global.exception.DomainErrorCode;

@Getter
@RequiredArgsConstructor
public enum AccountErrorCode implements DomainErrorCode {
	
	// Value Object Pattern Error
	INVALID_NICKNAME_PATTERN(
			HttpStatus.BAD_REQUEST,
			"닉네임은 한글, 영문, 숫자 조합으로 1~8 자리여야 합니다."
	),
	
	INVALID_TAG_PATTERN(
			HttpStatus.BAD_REQUEST,
			"태그는 영문, 숫자 조합으로 1~8 자리여야 합니다."
	),
	
	
	;
	private final HttpStatus status;
	
	private final String message;
	
	@Override
	public String getCode() {
		return this.name();
	}
}
