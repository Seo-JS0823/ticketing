package ticketing.account.global.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import ticketing.account.global.exception.DomainErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
		Instant timestamp,
		int status,
		String path,
		String error,
		String code,
		String message,
		List<FieldViolation> errors
) {
	
	public record FieldViolation(String field, String reason) {}
	
	public static ErrorResponse of(DomainErrorCode errorCode, String path, Instant timestamp) {
		return of(errorCode, path, timestamp, null);
	}
	
	public static ErrorResponse of(DomainErrorCode errorCode, String path, Instant timestamp, List<FieldViolation> errors) {
		return new ErrorResponse(
				timestamp,
				errorCode.getStatus().value(),
				path,
				errorCode.getStatus().getReasonPhrase(),
				errorCode.getCode(),
				errorCode.getMessage(),
				errors
		);
	}
}
