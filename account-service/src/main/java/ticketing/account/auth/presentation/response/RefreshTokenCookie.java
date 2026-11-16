package ticketing.account.auth.presentation.response;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

import ticketing.account.auth.application.result.RefreshTokenResult;

/*
 * Refresh Token 쿠키
 * 
 * - HttpOnly: true
 * - Secure: true (localhost -> 브라우저 예외 허용)
 * - SameSite=Lax, Path=/api/v1/auth: 인증 API에만 전송
 */
public final class RefreshTokenCookie {

	public static final String NAME = "refresh_token";
	
	private static final String PATH = "/api/v1/auth";
	
	private RefreshTokenCookie() {}
	
	public static ResponseCookie of(RefreshTokenResult refreshToken) {
		return base(refreshToken.refreshToken())
				.maxAge(Duration.ofSeconds(refreshToken.expiresIn()))
				.build();
	}
	
	public static ResponseCookie expired() {
		return base("")
				.maxAge(Duration.ZERO)
				.build();
	}
	
	private static ResponseCookie.ResponseCookieBuilder base(String value) {
		return ResponseCookie.from(NAME, value)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path(PATH);
	}
}
