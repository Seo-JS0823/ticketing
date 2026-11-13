package ticketing.account.account.infra.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import ticketing.account.account.application.result.AccessTokenResult;
import ticketing.account.account.domain.model.AccountRole;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.global.security.JwtConfig;
import ticketing.account.global.security.JwtProperties;

public class JwtAccessTokenIssuerTest {

	private static final String ISSUER = "ticketing-account";
	
	private static final String KEY_ID = "test-key";
	
	private static final Duration TTL = Duration.ofMinutes(15);
	
	private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
	
	private final JwtConfig jwtConfig = new JwtConfig();
	
	private final JwtProperties properties = properties(ISSUER);
	
	private final JwtDecoder decoder = jwtConfig.jwtDecoder(properties);
	
	@Test
	@DisplayName("발급한 토큰에 계정 정보와 만료 시각이 들어간다")
	void jwtAccessTokenIssuer_test1() {
		UUID accountId = UUID.randomUUID();
		
		AccessTokenResult result = issuer(properties, now)
				.issue(accountId, AccountRole.USER, AccountStatus.ACTIVE);
		
		Jwt jwt = decoder.decode(result.accessToken());
		
		assertThat(result.expiresIn()).isEqualTo(900);
		assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", KEY_ID);
		assertThat(jwt.getSubject()).isEqualTo(accountId.toString());
		assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
		assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
		
		assertThat(jwt.getClaimAsString("status")).isEqualTo("ACTIVE");
		assertThat(jwt.getIssuedAt()).isEqualTo(now);
		assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(TTL));
	}
	
	@Test
	@DisplayName("만료된 토큰 거부")
	void jwtAccessTokenIssuer_test2() {
		Instant issuedAt = now.minus(Duration.ofMinutes(16));
		
		String token = issuer(properties, issuedAt)
				.issue(UUID.randomUUID(), AccountRole.USER, AccountStatus.ACTIVE)
				.accessToken();
		
		assertThatThrownBy(() -> decoder.decode(token))
				.isInstanceOf(JwtException.class);
	}
	
	@Test
	@DisplayName("발급자가 다른 토큰은 거부")
	void jwtAccessTokenIssuer_test3() {
		String token = issuer(properties("other-service"), now)
				.issue(UUID.randomUUID(), AccountRole.USER, AccountStatus.ACTIVE)
				.accessToken();
		
		assertThatThrownBy(() -> decoder.decode(token))
				.isInstanceOf(JwtException.class);
	}
	
	@Test
	@DisplayName("내용을 바꾼 토큰은 서명이 맞지 않아 거부")
	void jwtAccessTokenIssuer_test4() {
		String token = issuer(properties, now)
				.issue(UUID.randomUUID(), AccountRole.USER, AccountStatus.ACTIVE)
				.accessToken();
		
		String[] parts = token.split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
		String forgedPayload = Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(payload.replace("\"USER\"", "\"ADMIN\"").getBytes(StandardCharsets.UTF_8));
		String forged = parts[0] + "." + forgedPayload + "." + parts[2];
		
		assertThatThrownBy(() -> decoder.decode(forged))
				.isInstanceOf(JwtException.class);
	}
	
	private JwtAccessTokenIssuer issuer(JwtProperties properties, Instant issuedAt) {
		return new JwtAccessTokenIssuer(
				jwtConfig.jwtEncoder(properties),
				properties,
				Clock.fixed(issuedAt, ZoneOffset.UTC)
		);
	}
	
	private static JwtProperties properties(String issuer) {
		return new JwtProperties(
				issuer,
				KEY_ID,
				TTL,
				new ClassPathResource("jwt/test-private.pem"),
				new ClassPathResource("jwt/test-public.pem")
		);
	}
}
