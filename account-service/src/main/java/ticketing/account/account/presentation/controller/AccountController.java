package ticketing.account.account.presentation.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.result.MyAccountResult;
import ticketing.account.account.application.result.WithdrawalResult;
import ticketing.account.account.application.service.AccountService;
import ticketing.account.account.application.service.SignupService;
import ticketing.account.account.presentation.request.EmailSignupRequest;
import ticketing.account.account.presentation.request.KakaoSignupRequest;
import ticketing.account.account.presentation.request.WithdrawalRequest;
import ticketing.account.auth.application.result.AuthResult;
import ticketing.account.auth.presentation.request.ClientType;
import ticketing.account.auth.presentation.response.RefreshTokenDelivery;
import ticketing.account.auth.presentation.response.TokenResponse;
import ticketing.account.auth.presentation.response.TokenResponses;
import ticketing.account.global.response.GlobalResponse;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

	private final SignupService signupService;
	
	private final AccountService accountService;
	
	// 이메일 회원가입
	@PostMapping
	public ResponseEntity<GlobalResponse<TokenResponse>> signup(
			@RequestBody EmailSignupRequest request,
			@RequestHeader(name = "X-Client-Type", defaultValue = "WEB") ClientType clientType
	) {
		AuthResult result = signupService.signupWithEmail(
				request.password(),
				request.nickname(),
				request.verificationToken()
		);
		
		return TokenResponses.of(
				HttpStatus.CREATED,
				result,
				RefreshTokenDelivery.from(clientType)
		);
	}
	
	// 카카오 회원가입 완료
	@PostMapping("/kakao")
	public ResponseEntity<GlobalResponse<TokenResponse>> signupWithKakao(
			@RequestBody KakaoSignupRequest request,
			@RequestHeader(name = "X-Client-Type", defaultValue = "WEB") ClientType clientType
	) {
		AuthResult result = signupService.signupWithKakao(
				request.signupToken(),
				request.verificationToken(),
				request.nickname()
		);
		
		return TokenResponses.of(
				HttpStatus.CREATED,
				result,
				RefreshTokenDelivery.from(clientType)
		);
	}
	
	@GetMapping("/me")
	public ResponseEntity<GlobalResponse<MyAccountResult>> getMyAccount(
			@AuthenticationPrincipal Jwt jwt
	) {
		MyAccountResult result = accountService.getMyAccount(UUID.fromString(jwt.getSubject()));
		
		return ResponseEntity.ok(GlobalResponse.of(result));
	}
	
	@PostMapping("/me/withdrawal")
	public ResponseEntity<GlobalResponse<WithdrawalResult>> withdraw(
			@AuthenticationPrincipal Jwt jwt,
			@RequestBody WithdrawalRequest request,
			HttpServletRequest servletRequest
	) {
		WithdrawalResult result = accountService.withdraw(
				UUID.fromString(jwt.getSubject()),
				request.currentPassword(),
				request.verificationToken(),
				servletRequest.getRemoteAddr()
		);
		
		return ResponseEntity.ok(GlobalResponse.of(result));
	}
}
