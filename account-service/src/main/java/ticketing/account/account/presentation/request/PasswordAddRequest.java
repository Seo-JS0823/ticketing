package ticketing.account.account.presentation.request;

public record PasswordAddRequest(
		String password,
		String verificationToken
) {

}
