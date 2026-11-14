package ticketing.account.account.application.properties;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "app.kakao")
public record KakaoProperties(
		String clientId,
		String clientSecret,
		Long appId,
		List<String> allowedRedirectUris,
		@NotBlank String authBaseUrl,
		@NotBlank String apiBaseUrl,
		@NotNull Duration connectTimeout,
		@NotNull Duration readTimeout
) {

	public KakaoProperties {
		allowedRedirectUris = allowedRedirectUris == null
				? List.of()
				: List.copyOf(allowedRedirectUris);
	}
	
	public boolean isAllowedRedirectUri(String redirectUri) {
		return redirectUri != null &&
				allowedRedirectUris.contains(redirectUri);
	}
}
