package ticketing.account.account.presentation.request;

public record PasswordResetRequest(
		String verificationToken,
		String newPassword
) {

}
