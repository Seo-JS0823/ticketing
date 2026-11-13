package ticketing.account.account.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
import ticketing.account.account.domain.exception.EmailVerificationErrorCode;
import ticketing.account.account.domain.exception.EmailVerificationException;
import ticketing.account.global.exception.RateLimitExceededException;

@Entity
@Table(name = "email_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class EmailVerification {

	private static final Duration CODE_TTL = Duration.ofMinutes(10);
	
	private static final Duration TOKEN_TTL = Duration.ofMinutes(10);
	
	private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
	
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
	
	// 실패 시 상태 변경이 저장되어야 함
	// 서비스는 이 예외로 롤백하지 않아야 함
	public void verify(String submittedCodeHash, String newTokenHash, Instant now) {
		validatePending();
		
		if (!now.isBefore(this.expiresAt)) {
			this.status = EmailVerificationStatus.EXPIRED;
			throw new EmailVerificationException(EmailVerificationErrorCode.VERIFICATION_EXPIRED);
		}
		
		if (!matchesCode(submittedCodeHash)) {
			recordFailedAttempt();
		}
		
		this.status = EmailVerificationStatus.VERIFIED;
		this.verifiedAt = now;
		this.tokenHash = newTokenHash;
		this.tokenExpiresAt = now.plus(TOKEN_TTL);
	}
	
	public void consume(EmailVerificationPurpose expectedPurpose, Instant now) {
		boolean usable = this.status == EmailVerificationStatus.VERIFIED
				&& this.consumedAt == null
				&& this.purpose == expectedPurpose
				&& now.isBefore(this.tokenExpiresAt);
		
		if (!usable) {
			throw new EmailVerificationException(EmailVerificationErrorCode.INVALID_VERIFICATION_TOKEN);
		}
		
		this.consumedAt = now;
	}
	
	// 재발송 전 기존 요청 무효화
	public void cancelForResend(Instant now) {
		validatePending();
		
//		if (now.isBefore(this.createdAt.plus(RESEND_INTERVAL))) {
//			throw new EmailVerificationException(CommonErrorCode.TOO_MANY_REQUESTS);
//		}
//		
//		this.status = EmailVerificationStatus.CANCELLED;
		
		Instant resendableAt = this.createdAt.plus(RESEND_INTERVAL);
		
		if (now.isBefore(resendableAt)) {
			throw new RateLimitExceededException(Duration.between(now, resendableAt));
		}
		
		this.status = EmailVerificationStatus.CANCELLED;
	}
	
	public void cancel() {
		validatePending();
		
		this.status = EmailVerificationStatus.CANCELLED;
	}
	
	private void validatePending() {
		if (this.status != EmailVerificationStatus.PENDING) {
			throw new EmailVerificationException(EmailVerificationErrorCode.VERIFICATION_NOT_PENDING);
		}
	}
	
	private boolean matchesCode(String submittedCodeHash) {
		return MessageDigest.isEqual(
				this.codeHash.getBytes(StandardCharsets.UTF_8),
				submittedCodeHash.getBytes(StandardCharsets.UTF_8)
		);
	}
	
	private void recordFailedAttempt() {
		this.failedAttempts++;
		
		if (this.failedAttempts >= MAX_FAILED_ATTEMPTS) {
			this.status = EmailVerificationStatus.CANCELLED;
			throw new EmailVerificationException(EmailVerificationErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
		}
		
		throw new EmailVerificationException(EmailVerificationErrorCode.INVALID_VERIFICATION_CODE);
	}
}
