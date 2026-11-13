package ticketing.account.account.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.account.account.domain.exception.AuthErrorCode;
import ticketing.account.account.domain.exception.AuthException;

@Entity
@Table(name = "social_signup_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class SocialSignupToken {

	public static final Duration TTL = Duration.ofMinutes(30);
	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	
	@Column(length = 30, nullable = false, updatable = false)
	@Enumerated(EnumType.STRING)
	private SocialProvider provider;
	
	@Column(name = "provider_user_id", nullable = false, updatable = false)
	private String providerUserId;
	
	@Column(name = "provider_email", length = 320, updatable = false)
	private String providerEmail;
	
	@Column(name = "token_hash", nullable = false, updatable = false, length = 64)
	private String tokenHash;
	
	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;
	
	@Column(name = "consumed_at")
	private Instant consumedAt;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	
	public static SocialSignupToken issue(
			SocialProvider provider,
			String providerUserId,
			String providerEmail,
			String tokenHash,
			Instant now
	) {
		SocialSignupToken token = new SocialSignupToken();
		
		token.provider = provider;
		token.providerUserId = providerUserId;
		token.providerEmail = providerEmail;
		token.tokenHash = tokenHash;
		token.expiresAt = now.plus(TTL);
		token.createdAt = now;
		
		return token;
	}
	
	public void consume(Instant now) {
		boolean usable = this.consumedAt == null
				&& now.isBefore(this.expiresAt);
		
		if (!usable) {
			throw new AuthException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
		}
		
		this.consumedAt = now;
	}
}
