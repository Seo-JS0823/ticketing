package ticketing.account.account.domain.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.account.global.exception.DomainErrorCode;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements DomainErrorCode {

	INVALID_REFRESH_TOKEN(
			HttpStatus.UNAUTHORIZED,
			"잘못되었거나 만료된 인증 정보입니다."
	),
	
	INVALID_SOCIAL_CREDENTIAL(
			HttpStatus.UNAUTHORIZED,
			"소셜 인증 정보를 확인할 수 없습니다."
	),
	
	INVALID_SIGNUP_TOKEN(
			HttpStatus.BAD_REQUEST,
			"잘못되었거나 만료된 가입 토큰입니다."
	),
	
	ACCOUNT_WITHDRAWN(
			HttpStatus.FORBIDDEN,
			"탈퇴한 계정입니다. 계정 복구를 진행해 주세요."
	),
	
	;
	private final HttpStatus status;
	
	private final String message;

	@Override
	public String getCode() {
		return this.name();
	}
}
