package ticketing.account.account.presentation.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.result.EmailVerificationResult;
import ticketing.account.account.application.result.EmailVerificationTokenResult;
import ticketing.account.account.application.service.EmailVerificationService;
import ticketing.account.account.presentation.request.EmailVerificationCreateRequest;
import ticketing.account.account.presentation.request.EmailVerificationVerifyRequest;
import ticketing.account.global.response.GlobalResponse;

@RestController
@RequestMapping("/api/v1/email-verifications")
@RequiredArgsConstructor
public class EmailVerificationController {

	private final EmailVerificationService emailVerificationService;
	
	/*
	 * 인증 코드 요청
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public GlobalResponse<EmailVerificationResult> request(
			@Valid @RequestBody EmailVerificationCreateRequest request,
			HttpServletRequest servletRequest
	) {
		
		return GlobalResponse.of(
				emailVerificationService.request(
						request.email(),
						request.purpose(),
						servletRequest.getRemoteAddr()
				)
		);
	}
	
	/*
	 * 인증 코드 재발송
	 */
	@PostMapping("/{id}/resend")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public GlobalResponse<EmailVerificationResult> resend(
			@PathVariable("id") UUID id,
			HttpServletRequest servletRequest
	) {
		return GlobalResponse.of(
				emailVerificationService.resend(id, servletRequest.getRemoteAddr())
		);
	}
	
	/*
	 * 인증 코드 검증
	 */
	@PostMapping("/{id}/verify")
	public GlobalResponse<EmailVerificationTokenResult> verify(
			@PathVariable("id") UUID id,
			@Valid @RequestBody EmailVerificationVerifyRequest request
	) {
		return GlobalResponse.of(
				emailVerificationService.verify(id, request.code())
		);
	}
	
	/*
	 * 인증 코드 요청 취소
	 */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(@PathVariable("id") UUID id) {
		emailVerificationService.cancel(id);
	}
}
