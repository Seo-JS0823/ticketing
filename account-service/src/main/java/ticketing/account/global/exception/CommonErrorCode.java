package ticketing.account.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements DomainErrorCode {

	INVALID_REQUEST(
			HttpStatus.BAD_REQUEST,
			"요청 값이 올바르지 않습니다."
	),
	
	UNAUTHORIZED(
			HttpStatus.UNAUTHORIZED,
			"인증이 필요합니다."
	),
	
	FORBIDDEN(
			HttpStatus.FORBIDDEN,
			"접근 권한이 없습니다."
	),
	
	RESOURCE_NOT_FOUND(
			HttpStatus.NOT_FOUND,
			"요청한 경로를 찾을 수 없습니다."
	),
	
	METHOD_NOT_ALLOWED(
			HttpStatus.METHOD_NOT_ALLOWED,
			"지원하지 않는 HTTP 메서드입니다."
	),
	
	UNSUPPORTED_MEDIA_TYPE(
			HttpStatus.UNSUPPORTED_MEDIA_TYPE,
			"지원하지 않는 Content-Type 입니다."
	),
	
	INTERNAL_SERVER_ERROR(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"서버 오류가 발생했습니다."
	),
	
	TOO_MANY_REQUESTS(
			HttpStatus.TOO_MANY_REQUESTS,
			"잠시 후 다시 시도해 주세요."
	),
	
	;
	private final HttpStatus status;
	
	private final String message;

	@Override
	public String getCode() {
		return this.name();
	}
}
