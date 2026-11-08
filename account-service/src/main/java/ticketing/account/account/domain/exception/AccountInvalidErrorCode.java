package ticketing.account.account.domain.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.account.global.exception.DomainErrorCode;

@Getter
@RequiredArgsConstructor
public enum AccountInvalidErrorCode implements DomainErrorCode {

	// Value Object Pattern Error
	INVALID_NICKNAME_PATTERN(
			HttpStatus.BAD_REQUEST,
			"닉네임은 한글, 영문, 숫자 조합으로 2~10자여야 합니다."
	),
	
	INVALID_TAG_PATTERN(
			HttpStatus.BAD_REQUEST,
			"태그는 한글, 영문, 숫자 조합으로 4~8자여야 합니다."
	),
	
	INVALID_EMAIL_PATTERN(
			HttpStatus.BAD_REQUEST,
			"올바르지 않은 이메일 형식입니다."
	),
	
	// Empty Error
	EMAIL_REQUIRED(
			HttpStatus.BAD_REQUEST,
			"이메일은 필수입니다."
	),
	
	// Size Error
	EMAIL_TOO_LONG(
			HttpStatus.BAD_REQUEST,
			"이메일은 320자 이내여야 합니다."
	);
	;
	private final HttpStatus status;
	
	private final String message;
	
	@Override
	public String getCode() {
		return this.name();
	}
}
