package ticketing.account.account.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.service.PasswordService;
import ticketing.account.account.presentation.request.PasswordResetRequest;
import ticketing.account.global.response.GlobalResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class PasswordController {

	private final PasswordService passwordService;
	
	@PostMapping("/password-reset")
	public ResponseEntity<GlobalResponse<Void>> resetPassword(
			@RequestBody PasswordResetRequest request,
			HttpServletRequest servletRequest
	) {
		passwordService.resetPassword(
				request.verificationToken(),
				request.newPassword(),
				servletRequest.getRemoteAddr()
		);
		
		return ResponseEntity.ok(GlobalResponse.success());
	}
}
