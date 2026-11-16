package ticketing.account.account.presentation.request;

public record PasswordChangeRequest(
		String currentPassword,
		String newPassword
) {

}
