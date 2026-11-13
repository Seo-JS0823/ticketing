package ticketing.account.account.domain.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ticketing.account.global.exception.DomainErrorCode;

@Getter
@RequiredArgsConstructor
public enum AccountErrorCode implements DomainErrorCode {
	
	CREDENTIAL_NOT_REGISTERED(
			HttpStatus.CONFLICT,
			"비밀번호가 등록되어 있지 않습니다."
	),
	
	CREDENTIAL_ALREADY_REGISTERED(
			HttpStatus.CONFLICT,
			"이미 비밀번호가 등록되어 있습니다."
	),
	
	ACCOUNT_NOT_ACTIVE(
			HttpStatus.CONFLICT,
			"계정이 활성 상태가 아니므로 작업을 수행할 수 없습니다."
	),
	
	SOCIAL_ACCOUNT_ALREADY_CONNECTED(
			HttpStatus.CONFLICT,
			"이미 연결된 소셜 계정입니다."
	),
	
	EMAIL_ALREADY_IN_USE(
			HttpStatus.CONFLICT,
			"이미 사용 중인 이메일입니다."
	),
	
	;
	private final HttpStatus status;
	
	private final String message;
	
	@Override
	public String getCode() {
		return this.name();
	}
}
