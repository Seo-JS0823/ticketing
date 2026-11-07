package ticketing.account.global.response;

import java.time.LocalDateTime;

import org.springframework.web.context.request.WebRequest;

import lombok.Builder;
import lombok.Getter;
import ticketing.account.global.exception.DomainErrorCode;

@Getter
public class ErrorResponse {

	private LocalDateTime timestamp;
	
	private int status;
	
	private String path;
	
	private String error;
	
	private String code;
	
	private String message;
	
	@Builder
	private ErrorResponse(int status, String path, String error, String code, String message) {
		this.timestamp = LocalDateTime.now();
		this.status = status;
		this.path = path;
		this.error = error;
		this.code = code;
		this.message = message;
	}
	
	public static ErrorResponse of(DomainErrorCode errorCode, WebRequest request) {
		return ErrorResponse.builder()
				.status(errorCode.getStatus().value())
				.error(errorCode.getStatus().getReasonPhrase())
				.code(errorCode.getCode())
				.message(errorCode.getMessage())
				.path(extractPath(request))
				.build();
	}
	
	private static String extractPath(WebRequest request) {
		String description = request.getDescription(false);
		
		if (description.startsWith("uri=")) {
			return description.substring(4);
		}
		
		return description;
	}
}
