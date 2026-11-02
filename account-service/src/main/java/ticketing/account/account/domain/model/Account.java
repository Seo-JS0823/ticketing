package ticketing.account.account.domain.model;

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
import lombok.Getter;

@Entity
@Table(name = "accounts")
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
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	
	@Column(name = "withdrawn_at")
	private Instant withdrawnAt;
	
	@Column(name = "recovery_expires_at")
	private Instant recoveryExpiresAt;
}
