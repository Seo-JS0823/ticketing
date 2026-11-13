package ticketing.account.account.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class RefreshToken {

	public static final Duration TTL = Duration.ofDays(14);
	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	
	@Column(name = "session_id", nullable = false, updatable = false)
	private UUID sessionId;
	
	@Column(name = "account_id", nullable = false, updatable = false)
	private UUID accountId;
	
	@Column(name = "token_hash", nullable = false, updatable = false, length = 64)
	private String tokenHash;
	
	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;
	
	@Column(name = "used_at")
	private Instant usedAt;
	
	@Column(name = "revoked_at")
	private Instant revokedAt;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	
	private RefreshToken(UUID sessionId, UUID accountId, String tokenHash, Instant now) {
		this.sessionId = sessionId;
		this.accountId = accountId;
		this.tokenHash = tokenHash;
		this.expiresAt = now.plus(TTL);
		this.createdAt = now;
	}
	
	public static RefreshToken issue(UUID accountId, String tokenHash, Instant now) {
		return new RefreshToken(
				UUID.randomUUID(),
				accountId, 
				tokenHash,
				now
		);
	}
	
	public RefreshToken rotate(String newTokenHash, Instant now) {
		boolean usable = this.usedAt == null
				&& this.revokedAt == null
				&& now.isBefore(this.expiresAt);
		
		if (!usable) {
			throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		}
		
		this.usedAt = now;
		
		return new RefreshToken(this.sessionId, this.accountId, newTokenHash, now);
	}
	
	public boolean isUsed() {
		return this.usedAt != null;
	}
}
