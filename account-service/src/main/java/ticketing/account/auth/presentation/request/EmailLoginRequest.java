package ticketing.account.auth.presentation.request;

public record EmailLoginRequest(
		String email,
		String password
) {

}
