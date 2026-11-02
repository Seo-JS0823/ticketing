package ticketing.account.account.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "social_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@ManyToOne(
			fetch = FetchType.LAZY
	)
	@JoinColumn(name = "account_id", nullable = false, updatable = false)
	private Account account;
	
	@Column(length = 30)
	@Enumerated(EnumType.STRING)
	private SocialProvider provider;
	
	@Column(name = "provider_user_id", nullable = false)
	private String providerUserId;
	
	@Column(name = "linked_at", nullable = false)
	private Instant linkedAt;
	
	SocialAccount(Account account, SocialProvider provider, String providerUserId, Instant now) {
		this.account = account;
		this.provider = provider;
		this.providerUserId =providerUserId;
		this.linkedAt = now;
	}
	
}
