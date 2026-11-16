package ticketing.account.account.presentation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.service.SignupService;
import ticketing.account.account.presentation.request.EmailSignupRequest;
import ticketing.account.account.presentation.request.KakaoSignupRequest;
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
}
