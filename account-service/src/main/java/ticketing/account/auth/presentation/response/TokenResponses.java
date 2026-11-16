package ticketing.account.auth.presentation.response;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import ticketing.account.auth.application.result.AuthResult;
import ticketing.account.auth.application.result.RefreshTokenResult;
import ticketing.account.global.response.GlobalResponse;

public final class TokenResponses {

	private TokenResponses() {}
	
	public static ResponseEntity<GlobalResponse<TokenResponse>> of(
			HttpStatus status,
			AuthResult result,
			RefreshTokenDelivery delivery
	) {
		return withRefreshToken(status, result.refreshToken(), delivery)
				.body(GlobalResponse.of(TokenResponse.of(result, delivery.inBody())));
	}
	
	public static ResponseEntity.BodyBuilder withRefreshToken(
			HttpStatus status,
			RefreshTokenResult refreshToken,
			RefreshTokenDelivery delivery
	) {
		ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
		
		if (delivery == RefreshTokenDelivery.COOKIE) {
			response.header(
					HttpHeaders.SET_COOKIE,
					RefreshTokenCookie.of(refreshToken).toString()
			);
		}
		
		return response;
	}
}
