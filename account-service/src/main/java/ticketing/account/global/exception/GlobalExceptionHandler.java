package ticketing.account.global.exception;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.account.global.response.ErrorResponse;
import ticketing.account.global.response.ErrorResponse.FieldViolation;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

	private final Clock clock;
	
	@Value("${spring.application.name}")
	private String serviceName;
	
	@ExceptionHandler(DomainException.class)
	public ResponseEntity<ErrorResponse> handleDomainException(DomainException e, HttpServletRequest request) {
		log.warn("[{}] 도메인 예외 발생 errorCode={}", serviceName, e.getErrorCode().getCode());
		
		return toResponse(e.getErrorCode(), request, null);
	}
	
	@ExceptionHandler(RateLimitExceededException.class)
	public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException e, HttpServletRequest request) {
		log.warn("[{}] 요청 한도 초과 path={}", serviceName, request.getRequestURI());
		
		ErrorResponse body = ErrorResponse.of(
				e.getErrorCode(),
				request.getRequestURI(),
				Instant.now(clock)
		);
		
		return ResponseEntity
				.status(e.getErrorCode().getStatus())
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
				.body(body);
	}
	
	// @Valid @RequestBody 검증 실패
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleInvalidBody(
			MethodArgumentNotValidException e,
			HttpServletRequest request
	) {
		List<FieldViolation> violations = e.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
				.toList();
		
		return toResponse(CommonErrorCode.INVALID_REQUEST, request, violations);
	}
	
	// @RequestParam, @PathVariable 제약 조건 검증 실패
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleInvalidParameter(
			HandlerMethodValidationException e,
			HttpServletRequest request
	) {
		List<FieldViolation> violations = e.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> new FieldViolation(
								result.getMethodParameter().getParameterName(),
								error.getDefaultMessage()
						)))
				.toList();
		
		return toResponse(CommonErrorCode.INVALID_REQUEST, request, violations);
	}
	
	// JSON 형식 오류, 필수 파라미터. 헤더. 쿠키 누락, 타입 변환 실패
	@ExceptionHandler({
		HttpMessageNotReadableException.class,
		ServletRequestBindingException.class,
		MethodArgumentTypeMismatchException.class
	})
	public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest request) {
		log.debug("[{}] 잘못된 요청 {}", serviceName, e.getMessage());
		
		return toResponse(CommonErrorCode.INVALID_REQUEST, request, null);
	}
	
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
		return toResponse(CommonErrorCode.RESOURCE_NOT_FOUND, request, null);
	}
	
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
			HttpRequestMethodNotSupportedException e,
			HttpServletRequest request
	) {
		return toResponse(CommonErrorCode.METHOD_NOT_ALLOWED, request, null);
	}
	
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
			HttpMediaTypeNotSupportedException e,
			HttpServletRequest request
	) {
		return toResponse(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, request, null);
	}
	
	// @PreAuthroize 등 메서드 보안 검증 실패
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(
			AccessDeniedException e,
			HttpServletRequest request
	) {
		return toResponse(CommonErrorCode.FORBIDDEN, request, null);
	}
	
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
		log.error("[{}] 처리되지 않은 예외 발생 path={}", serviceName, request.getRequestURI(), e);
		
		return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR, request, null);
	}
	
	private ResponseEntity<ErrorResponse> toResponse(
			DomainErrorCode errorCode,
			HttpServletRequest request,
			List<FieldViolation> violations
	) {
		ErrorResponse body = ErrorResponse.of(
				errorCode,
				request.getRequestURI(),
				Instant.now(clock),
				violations
		);
		
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(body);
	}
}
