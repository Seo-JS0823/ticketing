package ticketing.account.account.application.result;

import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountStatus;

public record AuthResult(
		AccessTokenResult accessToken,
		RefreshTokenResult refreshToken,
		AccountSummary account
) {

	public record AccountSummary(
			String email,
			String nickname,
			String tag,
			AccountStatus status
	) {
		public static AccountSummary from(Account account) {
			return new AccountSummary(
					account.getEmail().getEmail(),
					account.getPublicIdentifier().getNickname(),
					account.getPublicIdentifier().getTag(),
					account.getStatus()
			);
		}
	}
}
