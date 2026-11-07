package ticketing.account.global.exception;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import lombok.extern.slf4j.Slf4j;
import ticketing.account.global.response.ErrorResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@Value("${spring.application.name}")
	private String serviceName;
	
	@ExceptionHandler(DomainException.class)
	public ResponseEntity<ErrorResponse> handleDomainException(DomainException e, WebRequest request) {
		DomainErrorCode errorCode = e.getErrorCode();
		
		ErrorResponse errorResponse = ErrorResponse.of(errorCode, request);
		
		log.warn("[{}] 도메인 예외 발생 errorCode={}", serviceName, errorCode.getCode());
		
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(errorResponse);
	}
}
