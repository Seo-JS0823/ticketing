package ticketing.account.account.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

@Entity
@Table(name = "email_verification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class EmailVerification {

	private static final Duration CODE_TTL = Duration.ofMinutes(10);
	
	private static final Duration TOKEN_TTL = Duration.ofMinutes(10);
	
	private static final Duration RESEND_INTERNAL = Duration.ofSeconds(60);
	
	private static final int MAX_FAILED_ATTEMPTS = 5;
	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	
	@Column(name = "account_id", updatable = false)
	private UUID accountId;
	
	@Embedded
	private Email email;
	
	@Column(nullable = false, updatable = false, length = 30)
	@Enumerated(EnumType.STRING)
	private EmailVerificationPurpose purpose;
	
	@Column(name = "code_hash", nullable = false, updatable = false, length = 64)
	private String codeHash;
	
	@Column(nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private EmailVerificationStatus status;
	
	@Column(name = "failed_attempts", nullable = false)
	private int failedAttempts;
	
	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;
	
	@Column(name = "verified_at")
	private Instant verifiedAt;
	
	@Column(name = "token_hash", length = 64)
	private String tokenHash;
	
	@Column(name = "token_expires_at")
	private Instant tokenExpiresAt;
	
	@Column(name = "consumed_at")
	private Instant consumedAt;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	
	public static EmailVerification request(
			UUID accountId,
			Email email,
			EmailVerificationPurpose purpose,
			String codeHash,
			Instant now
	) {
		EmailVerification verification = new EmailVerification();
		
		verification.accountId = accountId;
		verification.email = email;
		verification.purpose = purpose;
		verification.codeHash = codeHash;
		verification.status = EmailVerificationStatus.PENDING;
		verification.failedAttempts = 0;
		verification.expiresAt = now.plus(CODE_TTL);
		verification.createdAt = now;
		
		return verification;
	}
}
