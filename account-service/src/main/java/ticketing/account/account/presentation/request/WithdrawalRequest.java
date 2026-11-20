package ticketing.account.account.presentation.request;

public record WithdrawalRequest(
		String currentPassword,
		String verificationToken
) {

}
