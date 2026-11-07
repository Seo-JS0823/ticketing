package ticketing.account.account.domain.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountCredential {

	@Id
	@Column(name = "account_id", nullable = false)
	private UUID accountId;
	
	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;
	
	@Column(name = "password_hash", nullable = false)
	private String passwordHash;
	
	@Column(name = "password_changed_at", nullable = false)
	private Instant passwordChangedAt;
	
	AccountCredential(
			Account account,
			String passwordHash,
			Instant now
	) {
		this.account = account;
		this.passwordHash = passwordHash;
		this.passwordChangedAt = now;
	}
	
	void changePassword(String newPasswordHash, Instant now) {
		this.passwordHash = newPasswordHash;
		this.passwordChangedAt = now;
	}
}
