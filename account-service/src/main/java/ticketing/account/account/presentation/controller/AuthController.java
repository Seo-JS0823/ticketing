package ticketing.account.account.presentation.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.result.KakaoLoginResult;
import ticketing.account.account.application.service.KakaoLoginService;
import ticketing.account.account.presentation.request.ClientType;
import ticketing.account.account.presentation.request.KakaoLoginRequest;
import ticketing.account.account.presentation.response.KakaoLoginResponse;
import ticketing.account.account.presentation.response.RefreshTokenCookie;
import ticketing.account.global.exception.CommonErrorCode;
import ticketing.account.global.exception.DomainException;
import ticketing.account.global.response.GlobalResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final KakaoLoginService kakaoLoginService;
	
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