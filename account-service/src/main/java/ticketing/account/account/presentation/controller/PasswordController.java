package ticketing.account.account.presentation.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.service.PasswordService;
import ticketing.account.account.presentation.request.PasswordAddRequest;
import ticketing.account.account.presentation.request.PasswordChangeRequest;
import ticketing.account.account.presentation.request.PasswordResetRequest;
import ticketing.account.global.response.GlobalResponse;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PasswordController {

	private final PasswordService passwordService;
	
	// 비밀번호 재설정 (비밀번호를 잊은 사용자)
	@PostMapping("/auth/password-reset")
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
	
	// 비밀번호 추가 (소셜 전용 계정)
	@PostMapping("/accounts/me/password")
	public ResponseEntity<GlobalResponse<Void>> addPassword(
			@AuthenticationPrincipal Jwt jwt,
			@RequestBody PasswordAddRequest request
	) {
		passwordService.addPassword(
				UUID.fromString(jwt.getSubject()),
				request.password(),
				request.verificationToken()
		);
		
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(GlobalResponse.success());
	}
	
	// 비밀번호 변경
	@PatchMapping("/accounts/me/password")
	public ResponseEntity<GlobalResponse<Void>> changePassword(
			@AuthenticationPrincipal Jwt jwt,
			@RequestBody PasswordChangeRequest request,
			HttpServletRequest servletRequest
	) {
		passwordService.changePassword(
				UUID.fromString(jwt.getSubject()),
				request.currentPassword(),
				request.newPassword(),
				servletRequest.getRemoteAddr()
		);
		
		return ResponseEntity.ok(GlobalResponse.success());
	}
}
