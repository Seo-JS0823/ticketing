package ticketing.account.auth.presentation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import ticketing.account.auth.application.result.AuthResult;
import ticketing.account.auth.application.result.KakaoLoginResult;
import ticketing.account.auth.application.service.EmailLoginService;
import ticketing.account.auth.application.service.KakaoLoginService;
import ticketing.account.auth.presentation.request.ClientType;
import ticketing.account.auth.presentation.request.EmailLoginRequest;
import ticketing.account.auth.presentation.request.KakaoLoginRequest;
import ticketing.account.auth.presentation.response.KakaoLoginResponse;
import ticketing.account.auth.presentation.response.RefreshTokenDelivery;
import ticketing.account.auth.presentation.response.TokenResponse;
import ticketing.account.auth.presentation.response.TokenResponses;
import ticketing.account.global.exception.CommonErrorCode;
import ticketing.account.global.exception.DomainException;
import ticketing.account.global.response.GlobalResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final EmailLoginService emailLoginService;
	
	private final KakaoLoginService kakaoLoginService;
	
	// 이메일 로그인
	@PostMapping("/login")
	public ResponseEntity<GlobalResponse<TokenResponse>> emailLogin(
			@RequestBody EmailLoginRequest request,
			@RequestHeader(name = "X-Client-Type", defaultValue = "WEB") ClientType clientType,
			HttpServletRequest servletRequest
	) {
		AuthResult result = emailLoginService.login(
				request.email(),
				request.password(),
				servletRequest.getRemoteAddr()
		);
		
		return TokenResponses.of(
				HttpStatus.OK,
				result,
				RefreshTokenDelivery.from(clientType)
		);
	}
	
	// 카카오 로그인
	@PostMapping("/kakao")
	public ResponseEntity<GlobalResponse<KakaoLoginResponse>> kakaoLogin(
			@RequestBody KakaoLoginRequest request,
			@RequestHeader(name = "X-Client-Type", defaultValue = "WEB") ClientType clientType
	) {
		KakaoLoginResult result = login(request);
		
		if (result.status() != KakaoLoginResult.Status.LOGIN) {
			return ResponseEntity.ok(GlobalResponse.of(KakaoLoginResponse.of(result, false)));
		}
		
		RefreshTokenDelivery delivery = RefreshTokenDelivery.from(clientType);
		
		return TokenResponses.withRefreshToken(
				HttpStatus.OK,
				result.auth().refreshToken(),
				delivery
		)
		.body(GlobalResponse.of(KakaoLoginResponse.of(result, delivery.inBody())));
	}
	
	private KakaoLoginResult login(KakaoLoginRequest request) {
		if (request.isWebRequest()) {
			return kakaoLoginService.loginWithAuthorizationCode(
					request.authorizationCode(),
					request.redirectUri()
			);
		}
		
		if (request.isAppRequest()) {
			return kakaoLoginService.loginWithAccessToken(request.kakaoAccessToken());
		}
		
		throw new DomainException(CommonErrorCode.INVALID_REQUEST);
	}
}