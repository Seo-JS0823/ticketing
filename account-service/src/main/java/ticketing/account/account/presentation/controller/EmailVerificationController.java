package ticketing.account.account.presentation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.EmailVerificationResult;
import ticketing.account.account.application.service.EmailVerificationService;
import ticketing.account.account.presentation.request.EmailVerificationCreateRequest;
import ticketing.account.global.response.GlobalResponse;

@RestController
@RequestMapping("/api/v1/email-verifications")
@RequiredArgsConstructor
public class EmailVerificationController {

	private final EmailVerificationService emailVerificationService;
	
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public GlobalResponse<EmailVerificationResult> request(
			@Valid @RequestBody EmailVerificationCreateRequest request
	) {
		
		return GlobalResponse.of(
				emailVerificationService.request(request.email(), request.purpose())
		);
	}
}
