package ticketing.account.account.presentation.request;

public record EmailLoginRequest(
		String email,
		String password
) {

}
