package ticketing.account.account.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountRole;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.model.SocialAccount;
import ticketing.account.account.domain.model.SocialProvider;

public record MyAccountResult(
		UUID accountId,
		String email,
		Instant emailVerifiedAt,
		String nickname,
		String tag,
		AccountRole role,
		AccountStatus status,
		boolean hasPassword,
		List<SocialAccountResult> socialAccounts,
		Instant createdAt
) {

	public static MyAccountResult from(Account account) {
		List<SocialAccountResult> socialAccounts = account.getActiveSocialAccounts().stream()
				.map(SocialAccountResult::from)
				.toList(); 
		
		return new MyAccountResult(
				account.getId(),
				account.getEmail().getEmail(),
				account.getEmailVerifiedAt(),
				account.getPublicIdentifier().getNickname(),
				account.getPublicIdentifier().getTag(),
				account.getRole(),
				account.getStatus(),
				account.hasPassword(),
				socialAccounts,
				account.getCreatedAt()
		);
	}
	
	public record SocialAccountResult(
			SocialProvider provider,
			Instant linkedAt
	) {
		static SocialAccountResult from(SocialAccount socialAccount) {
			return new SocialAccountResult(
					socialAccount.getProvider(),
					socialAccount.getLinkedAt()
			);
		}
	}
}
