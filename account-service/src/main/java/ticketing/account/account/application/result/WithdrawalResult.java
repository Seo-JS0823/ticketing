package ticketing.account.account.application.result;

import java.time.Instant;

import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountStatus;

public record WithdrawalResult(
		AccountStatus status,
		Instant recoveryExpiresAt
) {
	public static WithdrawalResult from(Account account) {
		return new WithdrawalResult(account.getStatus(), account.getRecoveryExpiresAt());
	}
}
