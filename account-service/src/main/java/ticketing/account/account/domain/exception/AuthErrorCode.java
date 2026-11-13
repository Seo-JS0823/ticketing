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
	
	;
	private final HttpStatus status;
	
	private final String message;

	@Override
	public String getCode() {
		return this.name();
	}
}
