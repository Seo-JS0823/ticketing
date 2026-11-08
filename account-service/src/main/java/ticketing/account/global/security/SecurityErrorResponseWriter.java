package ticketing.account.global.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import ticketing.account.global.exception.DomainErrorCode;
import ticketing.account.global.response.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

	private final JsonMapper jsonMapper;
	
	private final Clock clock;
	
	public void write(
			HttpServletRequest request,
			HttpServletResponse response,
			DomainErrorCode errorCode
	) throws IOException {
		ErrorResponse body = ErrorResponse.of(errorCode, request.getRequestURI(), Instant.now(clock));
		
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		jsonMapper.writeValue(response.getOutputStream(), body);
	}
}
