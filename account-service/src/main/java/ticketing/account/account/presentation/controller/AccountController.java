package ticketing.account.account.presentation.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.result.AuthResult;
import ticketing.account.account.application.service.SignupService;
import ticketing.account.account.presentation.request.ClientType;
import ticketing.account.account.presentation.request.EmailSignupRequest;
import ticketing.account.account.presentation.response.RefreshTokenCookie;
import ticketing.account.account.presentation.response.TokenResponse;
import ticketing.account.global.response.GlobalResponse;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

	private final SignupService signupService;
	
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
		
		ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
		
		if (clientType == ClientType.WEB) {
			response.header(
					HttpHeaders.SET_COOKIE,
					RefreshTokenCookie.of(result.refreshToken()).toString()
			);
		}
		
		return response.body(GlobalResponse.of(TokenResponse.of(result, clientType == ClientType.APP)));
	}
}
