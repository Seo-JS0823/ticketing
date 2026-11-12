package ticketing.account.account.domain.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.account.global.exception.DomainErrorCode;

@Getter
@RequiredArgsConstructor
public enum EmailVerificationErrorCode implements DomainErrorCode {
	
	INVALID_VERIFICATION_CODE(
			HttpStatus.BAD_REQUEST,
			"인증 코드가 일치하지 않습니다."
	),
	
	VERIFICATION_EXPIRED(
			HttpStatus.BAD_REQUEST,
			"인증 코드가 만료되었습니다."
	),
	
	VERIFICATION_NOT_PENDING(
			HttpStatus.BAD_REQUEST,
			"대기 상태의 인증 요청이 아닙니다."
	),
	
	VERIFICATION_NOT_FOUND(
			HttpStatus.NOT_FOUND,
			"인증 요청을 찾을 수 없습니다."
	),
	
	VERIFICATION_ATTEMPTS_EXCEEDED(
			HttpStatus.TOO_MANY_REQUESTS,
			"인증 코드 시도 횟수를 초과했습니다."
	),
	
	INVALID_VERIFICATION_TOKEN(
			HttpStatus.BAD_REQUEST,
			"인증 완료 토큰이 올바르지 않거나 만료되었습니다."
	),
	
	;
	private final HttpStatus status;
	
	private final String message;

	@Override
	public String getCode() {
		return this.name();
	}
}
