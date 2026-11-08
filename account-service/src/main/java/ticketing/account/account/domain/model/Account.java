package ticketing.account.account.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.account.account.domain.exception.AccountErrorCode;
import ticketing.account.account.domain.exception.AccountException;

@Entity
@Table(name = "accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Account {
	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	
	@Embedded
	private PublicIdentifier publicIdentifier;
	
	@Embedded
	private Email email;
	
	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;
	
	@Column(nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private AccountRole role;
	
	@Column(nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private AccountStatus status;
	
	// 비밀번호
	@OneToOne(
			mappedBy = "account",
			cascade = CascadeType.ALL,
			orphanRemoval = true,
			fetch = FetchType.LAZY
	)
	private AccountCredential credential;
	
	// 연결된 소셜 계정
	@OneToMany(
			mappedBy = "account",
			cascade = CascadeType.ALL,
			orphanRemoval = true
	)
	private List<SocialAccount> socialAccounts = new ArrayList<>();
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	
	@Column(name = "withdrawn_at")
	private Instant withdrawnAt;
	
	@Column(name = "recovery_expires_at")
	private Instant recoveryExpiresAt;
	
	public static Account register(PublicIdentifier publicIdentifier, Email email, Instant now, Instant emailVerifiedAt) {
		Account account = new Account();
		
		account.publicIdentifier = publicIdentifier;
		account.email = email;
		account.emailVerifiedAt = emailVerifiedAt;
		
		account.role = AccountRole.USER;
		account.status = AccountStatus.ACTIVE;
		account.createdAt = now;
		account.updatedAt = now;
		
		account.withdrawnAt = null;
		account.recoveryExpiresAt = null;
		
		return account;
	}
	
	// 비밀번호 생성 및 연결
	public void registerPassword(String passwordHash, Instant now) {
		validateNotWithdrawn();
		
		if (this.credential != null) {
			throw new AccountException(AccountErrorCode.CREDENTIAL_ALREADY_REGISTERED);
		}
		
		this.credential = new AccountCredential(this, passwordHash, now);
		this.updatedAt = now;
	}
	
	public void connectSocialAccount(SocialProvider provider, String providerUserId, Instant now) {
		validateActive();
		
		boolean exists = socialAccounts.stream()
				.anyMatch(social -> social.isActive() && social.getProvider() == provider);
		
		if (exists) {
			throw new AccountException(AccountErrorCode.SOCIAL_ACCOUNT_ALREADY_CONNECTED);
		}
		
		socialAccounts.add(new SocialAccount(this, provider, providerUserId, now));
		this.updatedAt = now;
	}
	
	// 이메일 변경
	public void changeEmail(String newEmail) {
		validateActive();
		
		Email email = Email.of(newEmail);
		
		this.email = email;
	}
	
	// 닉네임&태그 변경
	public void changePublicIdentifier(String nickname, String tag) {
		validateActive();
		
		PublicIdentifier publicIdentifier = PublicIdentifier.of(nickname, tag);
		
		this.publicIdentifier = publicIdentifier;
	}
	
	// 비밀번호 변경
	public void changePassword(String passwordHash, Instant now) {
		validateNotWithdrawn();
		
		if (this.credential == null) {
			throw new AccountException(AccountErrorCode.CREDENTIAL_NOT_REGISTERED);
		}
		
		this.credential.changePassword(passwordHash, now);
		this.updatedAt = now;
	}
	
	// 업데이트 전 활성 계정 검사
	private void validateActive() {
		if (this.status != AccountStatus.ACTIVE) {
			throw new AccountException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
		}
	}
	
	// 비밀번호 등 보안 작업 전 탈퇴 여부 검사
	private void validateNotWithdrawn() {
		if (this.status == AccountStatus.WITHDRAWN) {
			throw new AccountException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
		}
	}
}
