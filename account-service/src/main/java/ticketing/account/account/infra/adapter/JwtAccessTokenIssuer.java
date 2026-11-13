package ticketing.account.account.infra.adapter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.port.AccessTokenIssuer;
import ticketing.account.account.application.result.AccessTokenResult;
import ticketing.account.account.domain.model.AccountRole;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.global.security.AccessTokenClaims;
import ticketing.account.global.security.JwtProperties;

@Component
@RequiredArgsConstructor
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

	private final JwtEncoder jwtEncoder;
	
	private final JwtProperties properties;
	
	private final Clock clock;
	
	@Override
	public AccessTokenResult issue(UUID accountId, AccountRole role, AccountStatus status) {
		Instant now = Instant.now(clock);
		Duration ttl = properties.accessTokenTtl();
		
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
				.keyId(properties.keyId())
				.build();
		
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.subject(accountId.toString())
				.issuedAt(now)
				.expiresAt(now.plus(ttl))
				.claim(AccessTokenClaims.ROLE, role.name())
				.claim(AccessTokenClaims.STATUS, status.name())
				.build();
		
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		
		return new AccessTokenResult(token, ttl.toSeconds());
	}
}
