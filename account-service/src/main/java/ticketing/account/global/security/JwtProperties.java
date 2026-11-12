package ticketing.account.global.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		@NotBlank String issuer,
		@NotBlank String keyId,
		@NotNull Duration accessTokenTtl,
		@NotNull Resource privateKey,
		@NotNull Resource publicKey
) {

}
