package ticketing.account.account.presentation.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.result.AuthResult;
import ticketing.account.account.application.result.KakaoLoginResult;
import ticketing.account.account.application.service.EmailLoginService;
import ticketing.account.account.application.service.KakaoLoginService;
import ticketing.account.account.presentation.request.ClientType;
import ticketing.account.account.presentation.request.EmailLoginRequest;
import ticketing.account.account.presentation.request.KakaoLoginRequest;
import ticketing.account.account.presentation.response.KakaoLoginResponse;
import ticketing.account.account.presentation.response.RefreshTokenCookie;
import ticketing.account.account.presentation.response.TokenResponse;
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
		
		ResponseEntity.BodyBuilder response = ResponseEntity.ok();
		
		// WEB은 Refresh Token을 쿠키로, APP은 바디로 전달
		if (clientType == ClientType.WEB) {
			response.header(
					HttpHeaders.SET_COOKIE,
					RefreshTokenCookie.of(result.refreshToken()).toString()
			);
		}
		
		return response.body(GlobalResponse.of(TokenResponse.of(result, clientType == ClientType.APP)));
	}
	
	// 카카오 로그인
	@PostMapping("/kakao")
	public ResponseEntity<GlobalResponse<KakaoLoginResponse>> kakaoLogin(
			@RequestBody KakaoLoginRequest request,
			@RequestHeader(name = "X-Client-Type", defaultValue = "WEB") ClientType clientType
	) {
		KakaoLoginResult result = login(request);
		
		ResponseEntity.BodyBuilder response = ResponseEntity.ok();
		
		if (result.status() == KakaoLoginResult.Status.LOGIN && clientType == ClientType.WEB) {
			response.header(
					HttpHeaders.SET_COOKIE,
					RefreshTokenCookie.of(result.auth().refreshToken()).toString()
			);
		}
		
		return response.body(GlobalResponse.of(KakaoLoginResponse.of(result, clientType == ClientType.APP)));
	}
	
	private KakaoLoginResult login(KakaoLoginRequest request) {
		if (request.isWebRequest()) {
			return kakaoLoginService.loginWithAuthorizationCode(request.authorizationCode(), request.redirectUri());
		}
		
		if (request.isAppRequest()) {
			return kakaoLoginService.loginWithAccessToken(request.kakaoAccessToken());
		}
		
		throw new DomainException(CommonErrorCode.INVALID_REQUEST);
	}
}